package dev.dunnam.diceanchors.assembly;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.Authority;
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

/**
 * Post-generation compliance validator that checks LLM responses against active anchors
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
        var anchorsToEnforce = filterByPolicy(context.activeAnchors(), context.policy());

        if (anchorsToEnforce.isEmpty()) {
            return ComplianceResult.compliant(Duration.between(start, Instant.now()));
        }

        var validationPrompt = buildValidationPrompt(anchorsToEnforce, context.responseText());
        var rawResponse = callModel(validationPrompt);
        var violations = parseViolations(rawResponse, anchorsToEnforce);
        var duration = Duration.between(start, Instant.now());

        violations.forEach(v -> logger.warn("compliance.violation anchorId={} authority={} description={}",
                v.anchorId(), v.anchorAuthority(), v.description()));

        var action = determineAction(violations);
        var compliant = violations.isEmpty();

        logger.info("compliance.check strategy=POST_GENERATION anchors={} result={} duration={}ms",
                anchorsToEnforce.size(), action, duration.toMillis());

        return new ComplianceResult(compliant, violations, action, duration);
    }

    private List<Anchor> filterByPolicy(List<Anchor> anchors, ComplianceContext.CompliancePolicy policy) {
        return anchors.stream()
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

    private String buildValidationPrompt(List<Anchor> anchors, String responseText) {
        var anchorLines = new StringBuilder();
        for (var anchor : anchors) {
            anchorLines.append("[").append(anchor.id()).append("] (")
                       .append(anchor.authority().name()).append("): ")
                       .append(anchor.text()).append("\n");
        }

        return """
                You are a compliance validator. Check if the following response contradicts any of the established facts.

                Established facts (MUST NOT be contradicted):
                %s
                Response to validate:
                %s

                For each fact, determine if the response contradicts it. Respond in JSON format:
                {"violations": [{"anchorId": "...", "description": "...", "confidence": 0.0-1.0}]}
                If no violations, respond: {"violations": []}
                """.formatted(anchorLines, responseText);
    }

    private String callModel(String prompt) {
        var response = chatModel.call(new Prompt(prompt));
        return response.getResult().getOutput().getText();
    }

    private List<ComplianceViolation> parseViolations(String rawResponse, List<Anchor> enforcedAnchors) {
        var anchorById = Map.copyOf(
                enforcedAnchors.stream().collect(
                        java.util.stream.Collectors.toMap(Anchor::id, a -> a, (a, b) -> a)));

        try {
            var json = stripCodeFences(rawResponse);
            var parsed = MAPPER.readValue(json, ValidationResponse.class);
            if (parsed.violations() == null || parsed.violations().isEmpty()) {
                return List.of();
            }

            var result = new ArrayList<ComplianceViolation>();
            for (var entry : parsed.violations()) {
                var anchor = anchorById.get(entry.anchorId());
                if (anchor == null) {
                    logger.warn("compliance.parse unknown anchorId={} in validator response — skipping",
                            entry.anchorId());
                    continue;
                }
                result.add(new ComplianceViolation(
                        anchor.id(),
                        anchor.text(),
                        anchor.authority(),
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
                .anyMatch(v -> v.anchorAuthority() == Authority.CANON);
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
    private record ViolationEntry(String anchorId, String description, double confidence) {}
}
