package dev.arcmem.core.assembly.compliance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.arcmem.core.memory.model.Authority;
import dev.arcmem.core.memory.model.MemoryUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Post-generation compliance validator that checks LLM responses against active units
 * using a secondary LLM call.
 * <p>
 * Sits at the middle tier of the enforcement spectrum: more expensive than prompt injection
 * but less infrastructure-intensive than constrained decoding. One additional LLM call per
 * validated response; only invoked when enforcement is configured for at least one authority
 * level.
 * <p>
 * JSON parsing uses {@link JsonIgnoreProperties} on all response models to tolerate
 * extraneous fields from LLM output — a lesson from the BatchConflictResult parse failure
 * (see Mistakes Log, 2026-02-27).
 * <p>
 * Thread safety: stateless Spring component; safe for concurrent use.
 */
@Component
public class PostGenerationValidator implements ComplianceEnforcer {

    private static final Logger logger = LoggerFactory.getLogger(PostGenerationValidator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatModel chatModel;

    public PostGenerationValidator(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public ComplianceResult enforce(ComplianceContext context) {
        var start = Instant.now();
        var unitsToEnforce = filterByPolicy(context.activeUnits(), context.policy());

        if (unitsToEnforce.isEmpty()) {
            return ComplianceResult.compliant(Duration.between(start, Instant.now()));
        }

        var validationPrompt = buildValidationPrompt(unitsToEnforce, context.responseText());
        var rawResponse = callModel(validationPrompt);
        var violations = parseViolations(rawResponse, unitsToEnforce);
        var duration = Duration.between(start, Instant.now());

        violations.forEach(v -> logger.warn("compliance.violation unitId={} authority={} description={}",
                                            v.unitId(), v.unitAuthority(), v.description()));

        var action = determineAction(violations);
        var compliant = violations.isEmpty();

        logger.info("compliance.check strategy=POST_GENERATION units={} result={} duration={}ms",
                    unitsToEnforce.size(), action, duration.toMillis());

        return new ComplianceResult(compliant, violations, action, duration);
    }

    private List<MemoryUnit> filterByPolicy(List<MemoryUnit> units, ComplianceContext.CompliancePolicy policy) {
        return units.stream()
                    .filter(a -> shouldEnforce(a.authority(), policy))
                    .toList();
    }

    private boolean shouldEnforce(Authority authority, ComplianceContext.CompliancePolicy policy) {
        return switch (authority) {
            case CANON -> policy.enforceCanon();
            case RELIABLE -> policy.enforceReliable();
            case UNRELIABLE -> policy.enforceUnreliable();
            case PROVISIONAL -> policy.enforceProvisional();
        };
    }

    private String buildValidationPrompt(List<MemoryUnit> units, String responseText) {
        var unitLines = new StringBuilder();
        for (var unit : units) {
            unitLines.append("[").append(unit.id()).append("] (")
                     .append(unit.authority().name()).append("): ")
                     .append(unit.text()).append("\n");
        }

        return """
                You are a compliance validator. Check if the following response contradicts any of the established facts.
                
                Established facts (MUST NOT be contradicted):
                %s
                Response to validate:
                %s
                
                For each fact, determine if the response contradicts it. Respond in JSON format:
                {"violations": [{"unitId": "...", "description": "...", "confidence": 0.0-1.0}]}
                If no violations, respond: {"violations": []}
                """.formatted(unitLines, responseText);
    }

    private String callModel(String prompt) {
        var response = chatModel.call(new Prompt(prompt));
        return response.getResult().getOutput().getText();
    }

    private List<ComplianceViolation> parseViolations(String rawResponse, List<MemoryUnit> enforcedUnits) {
        var unitById = Map.copyOf(
                enforcedUnits.stream().collect(
                        Collectors.toMap(MemoryUnit::id, a -> a, (a, b) -> a)));

        try {
            var json = stripCodeFences(rawResponse);
            var parsed = MAPPER.readValue(json, ValidationResponse.class);
            if (parsed.violations() == null || parsed.violations().isEmpty()) {
                return List.of();
            }

            var result = new ArrayList<ComplianceViolation>();
            for (var entry : parsed.violations()) {
                var unit = unitById.get(entry.unitId());
                if (unit == null) {
                    logger.warn("compliance.parse unknown unitId={} in validator response — skipping",
                                entry.unitId());
                    continue;
                }
                result.add(new ComplianceViolation(
                        unit.id(),
                        unit.text(),
                        unit.authority(),
                        entry.description(),
                        entry.confidence()));
            }
            return List.copyOf(result);
        } catch (Exception e) {
            logger.warn("compliance.parse failed — treating as no violations: {}", e.getMessage());
            return List.of();
        }
    }

    private ComplianceAction determineAction(List<ComplianceViolation> violations) {
        if (violations.isEmpty()) {
            return ComplianceAction.ACCEPT;
        }
        boolean canonViolated = violations.stream()
                                          .anyMatch(v -> v.unitAuthority() == Authority.CANON);
        return canonViolated ? ComplianceAction.REJECT : ComplianceAction.RETRY;
    }

    private static String stripCodeFences(String raw) {
        if (raw == null) {
            return "{}";
        }
        return raw.replaceAll("(?s)```[a-z]*\\s*", "").replaceAll("```", "").trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ValidationResponse(List<ViolationEntry> violations) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ViolationEntry(String unitId, String description, double confidence) {}
}
