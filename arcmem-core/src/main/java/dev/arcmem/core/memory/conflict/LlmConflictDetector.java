package dev.arcmem.core.memory.conflict;
import dev.arcmem.core.memory.budget.*;
import dev.arcmem.core.memory.canon.*;
import dev.arcmem.core.memory.conflict.*;
import dev.arcmem.core.memory.engine.*;
import dev.arcmem.core.memory.maintenance.*;
import dev.arcmem.core.memory.model.*;
import dev.arcmem.core.memory.mutation.*;
import dev.arcmem.core.memory.trust.*;
import dev.arcmem.core.assembly.budget.*;
import dev.arcmem.core.assembly.compaction.*;
import dev.arcmem.core.assembly.compliance.*;
import dev.arcmem.core.assembly.protection.*;
import dev.arcmem.core.assembly.retrieval.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.arcmem.core.prompt.PromptPathConstants;
import dev.arcmem.core.prompt.PromptTemplates;
import dev.arcmem.core.spi.llm.LlmCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Detects semantic contradictions between an incoming statement and existing units
 * using an LLM to evaluate factual consistency. Distinguishes genuine contradiction
 * from narrative progression.
 */
public class LlmConflictDetector implements ConflictDetector {

    private static final Logger logger = LoggerFactory.getLogger(LlmConflictDetector.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern CODE_FENCE = Pattern.compile("^```(?:json)?\\s*|\\s*```$");
    private final double llmConfidence;
    private final ChatModel chatModel;
    private final String modelName;
    private final LlmCallService llmCallService;

    public LlmConflictDetector(double llmConfidence, ChatModel chatModel, String modelName, LlmCallService llmCallService) {
        this.llmConfidence = llmConfidence;
        this.chatModel = chatModel;
        this.modelName = modelName;
        this.llmCallService = llmCallService;
    }

    public LlmConflictDetector(ChatModel chatModel, String modelName, LlmCallService llmCallService) {
        this(0.9, chatModel, modelName, llmCallService);
    }

    @Override
    public List<Conflict> detect(String incomingText, List<MemoryUnit> existingUnits) {
        if (existingUnits == null || existingUnits.isEmpty()) {
            return List.of();
        }
        var conflicts = new ArrayList<Conflict>();
        for (var unit : existingUnits) {
            logger.debug("Checking conflict between incoming='{}' and unit id={} text='{}'",
                         incomingText, unit.id(), unit.text());
            var result = evaluatePair(incomingText, unit);
            if (result != null) {
                conflicts.add(result);
            }
        }
        return conflicts;
    }

    @Override
    public Map<String, List<Conflict>> batchDetect(List<String> candidateTexts, List<MemoryUnit> existingUnits) {
        if (candidateTexts.isEmpty() || existingUnits.isEmpty()) {
            return candidateTexts.stream().collect(Collectors.toMap(c -> c, c -> List.of()));
        }
        try {
            return batchLlmConflictCheck(candidateTexts, existingUnits);
        } catch (Exception e) {
            logger.warn("Batch conflict detection failed, falling back to per-candidate: {}", e.getMessage());
            return batchDetectDefault(candidateTexts, existingUnits);
        }
    }

    private Map<String, List<Conflict>> batchDetectDefault(List<String> candidates, List<MemoryUnit> units) {
        var result = new LinkedHashMap<String, List<Conflict>>();
        for (var candidate : candidates) {
            result.put(candidate, detect(candidate, units));
        }
        return result;
    }

    private Map<String, List<Conflict>> batchLlmConflictCheck(List<String> candidates, List<MemoryUnit> units) {
        var unitTexts = units.stream().map(MemoryUnit::text).toList();
        var systemPrompt = "Identify semantic contradictions between candidates and existing units.";
        var userPrompt = PromptTemplates.render(PromptPathConstants.DICE_BATCH_CONFLICT_DETECTION, Map.of(
                "units", unitTexts,
                "candidates", candidates));

        var responseText = llmCallService.callBatched(systemPrompt, userPrompt);
        return parseBatchConflictResponse(responseText, candidates, units);
    }

    private Map<String, List<Conflict>> parseBatchConflictResponse(
            String responseText, List<String> candidates, List<MemoryUnit> units) {
        try {
            var json = responseText.replaceAll("(?s)```[a-z]*\\s*", "").replaceAll("```", "").trim();
            var parsed = MAPPER.readValue(json, BatchConflictResult.class);
            var unitByText = units.stream()
                    .collect(Collectors.toMap(MemoryUnit::text, a -> a, (a, b) -> a));
            var result = new LinkedHashMap<String, List<Conflict>>();
            var entries = parsed.results() != null ? parsed.results() : List.<BatchConflictResult.Entry>of();
            for (var entry : entries) {
                var matches = entry.contradictingUnits() != null
                        ? entry.contradictingUnits()
                        : List.<BatchConflictResult.UnitMatch>of();
                var conflicts = matches.stream()
                        .filter(match -> match.conflictType() != ConflictType.WORLD_PROGRESSION)
                        .map(match -> {
                            var unit = unitByText.get(match.unitText());
                            if (unit == null) {
                                return null;
                            }
                            var reason = match.reasoning() != null && !match.reasoning().isBlank()
                                    ? match.reasoning()
                                    : "batch LLM conflict";
                            var conflictType = match.conflictType() != null
                                    ? match.conflictType()
                                    : ConflictType.CONTRADICTION;
                            return new Conflict(unit, entry.candidate(), llmConfidence, reason,
                                    DetectionQuality.FULL, conflictType);
                        })
                        .filter(Objects::nonNull)
                        .toList();
                result.put(entry.candidate(), conflicts);
            }
            candidates.forEach(c -> result.putIfAbsent(c, List.of()));
            return result;
        } catch (Exception e) {
            logger.warn("Batch conflict parse failed — marking {} candidates as DEGRADED (ACON1): {}",
                    candidates.size(), e.getMessage());
            return markDegraded(candidates);
        }
    }

    /**
     * Return a degraded placeholder for each candidate so callers know detection
     * could not complete (ACON1: never silently fail-open on parse errors).
     */
    private Map<String, List<Conflict>> markDegraded(List<String> candidates) {
        return candidates.stream().collect(Collectors.toMap(
                c -> c,
                c -> List.of(new Conflict(null, c, 0.0,
                        "conflict detection degraded — parse failure",
                        DetectionQuality.DEGRADED))));
    }

    private Conflict evaluatePair(String incomingText, MemoryUnit unit) {
        var promptText = PromptTemplates.render(PromptPathConstants.DICE_CONFLICT_DETECTION, Map.of(
                "statement_a", incomingText,
                "statement_b", unit.text(),
                "unit_authority", unit.authority().name()));
        var prompt = new Prompt(promptText);
        var response = chatModel.call(prompt);
        var raw = response.getResult().getOutput().getText();
        logger.debug("LLM conflict response for unit {}: {}", unit.id(), raw);
        return parseResponse(raw, incomingText, unit);
    }

    private Conflict parseResponse(String raw, String incomingText, MemoryUnit unit) {
        var json = stripCodeFences(raw);
        try {
            var node = MAPPER.readTree(json);
            var contradicts = node.path("contradicts").asBoolean(false);
            var reasoning = node.path("reasoning").asText("");
            var explanation = node.path("explanation").asText("LLM-detected contradiction");
            if (contradicts) {
                var reason = !reasoning.isBlank() ? reasoning : explanation;
                return new Conflict(unit, incomingText, llmConfidence, reason,
                        DetectionQuality.FULL, parseConflictType(node.path("conflictType")));
            }
            return null;
        } catch (Exception e) {
            logger.warn("LLM conflict parse failed for unit {} — falling back to text scan: {}",
                    unit.id(), e.getMessage());
            if (raw != null && raw.toLowerCase().contains("true")) {
                return new Conflict(unit, incomingText, llmConfidence,
                        "LLM indicated contradiction (fallback parse)",
                        DetectionQuality.FALLBACK,
                        ConflictType.CONTRADICTION);
            }
            return new Conflict(unit, incomingText, 0.0,
                    "conflict detection degraded — parse failure",
                    DetectionQuality.DEGRADED);
        }
    }

    private ConflictType parseConflictType(com.fasterxml.jackson.databind.JsonNode conflictTypeNode) {
        if (conflictTypeNode == null || conflictTypeNode.isMissingNode() || conflictTypeNode.isNull()) {
            return ConflictType.CONTRADICTION;
        }
        var raw = conflictTypeNode.asText("");
        if (raw.isBlank()) {
            return ConflictType.CONTRADICTION;
        }
        try {
            return ConflictType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ConflictType.CONTRADICTION;
        }
    }

    static String stripCodeFences(String raw) {
        if (raw == null) {
            return "";
        }
        var trimmed = raw.strip();
        if (trimmed.startsWith("```")) {
            trimmed = CODE_FENCE.matcher(trimmed).replaceAll("");
        }
        return trimmed.strip();
    }
}
