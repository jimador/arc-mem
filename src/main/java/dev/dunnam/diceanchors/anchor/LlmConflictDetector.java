package dev.dunnam.diceanchors.anchor;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dunnam.diceanchors.prompt.PromptPathConstants;
import dev.dunnam.diceanchors.prompt.PromptTemplates;
import dev.dunnam.diceanchors.sim.engine.LlmCallService;
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
 * Detects semantic contradictions between an incoming statement and existing anchors
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
    public List<Conflict> detect(String incomingText, List<Anchor> existingAnchors) {
        if (existingAnchors == null || existingAnchors.isEmpty()) {
            return List.of();
        }
        var conflicts = new ArrayList<Conflict>();
        for (var anchor : existingAnchors) {
            logger.debug("Checking conflict between incoming='{}' and anchor id={} text='{}'",
                         incomingText, anchor.id(), anchor.text());
            var result = evaluatePair(incomingText, anchor);
            if (result != null) {
                conflicts.add(result);
            }
        }
        return conflicts;
    }

    @Override
    public Map<String, List<Conflict>> batchDetect(List<String> candidateTexts, List<Anchor> existingAnchors) {
        if (candidateTexts.isEmpty() || existingAnchors.isEmpty()) {
            return candidateTexts.stream().collect(Collectors.toMap(c -> c, c -> List.of()));
        }
        try {
            return batchLlmConflictCheck(candidateTexts, existingAnchors);
        } catch (Exception e) {
            logger.warn("Batch conflict detection failed, falling back to per-candidate: {}", e.getMessage());
            return batchDetectDefault(candidateTexts, existingAnchors);
        }
    }

    private Map<String, List<Conflict>> batchDetectDefault(List<String> candidates, List<Anchor> anchors) {
        var result = new LinkedHashMap<String, List<Conflict>>();
        for (var candidate : candidates) {
            result.put(candidate, detect(candidate, anchors));
        }
        return result;
    }

    private Map<String, List<Conflict>> batchLlmConflictCheck(List<String> candidates, List<Anchor> anchors) {
        var anchorTexts = anchors.stream().map(Anchor::text).toList();
        var systemPrompt = "Identify semantic contradictions between candidates and existing anchors.";
        var userPrompt = PromptTemplates.render(PromptPathConstants.DICE_BATCH_CONFLICT_DETECTION, Map.of(
                "anchors", anchorTexts,
                "candidates", candidates));

        var responseText = llmCallService.callBatched(systemPrompt, userPrompt);
        return parseBatchConflictResponse(responseText, candidates, anchors);
    }

    private Map<String, List<Conflict>> parseBatchConflictResponse(
            String responseText, List<String> candidates, List<Anchor> anchors) {
        try {
            var json = responseText.replaceAll("(?s)```[a-z]*\\s*", "").replaceAll("```", "").trim();
            var parsed = MAPPER.readValue(json, BatchConflictResult.class);
            // Build anchor text -> anchor lookup
            var anchorByText = anchors.stream()
                    .collect(Collectors.toMap(Anchor::text, a -> a, (a, b) -> a));
            var result = new LinkedHashMap<String, List<Conflict>>();
            for (var entry : parsed.results()) {
                var conflicts = entry.contradictingAnchors().stream()
                        .map(anchorByText::get)
                        .filter(Objects::nonNull)
                        .map(anchor -> new Conflict(anchor, entry.candidate(), llmConfidence,
                                "batch LLM conflict"))
                        .toList();
                result.put(entry.candidate(), conflicts);
            }
            candidates.forEach(c -> result.putIfAbsent(c, List.of()));
            return result;
        } catch (Exception e) {
            logger.warn("Failed to parse batch conflict response — falling back to no-conflict for all {} candidates (response truncated to 200 chars: '{}'): {}",
                    candidates.size(),
                    responseText != null ? responseText.substring(0, Math.min(200, responseText.length())) : "<null>",
                    e.getMessage());
            return candidates.stream().collect(Collectors.toMap(c -> c, c -> List.of()));
        }
    }

    private Conflict evaluatePair(String incomingText, Anchor anchor) {
        var promptText = PromptTemplates.render(PromptPathConstants.DICE_CONFLICT_DETECTION, Map.of(
                "statement_a", incomingText,
                "statement_b", anchor.text()));
        var prompt = new Prompt(promptText);
        var response = chatModel.call(prompt);
        var raw = response.getResult().getOutput().getText();
        logger.debug("LLM conflict response for anchor {}: {}", anchor.id(), raw);
        return parseResponse(raw, incomingText, anchor);
    }

    private Conflict parseResponse(String raw, String incomingText, Anchor anchor) {
        var json = stripCodeFences(raw);
        try {
            var node = MAPPER.readTree(json);
            var contradicts = node.path("contradicts").asBoolean(false);
            var explanation = node.path("explanation").asText("LLM-detected contradiction");
            if (contradicts) {
                return new Conflict(anchor, incomingText, llmConfidence, explanation);
            }
            return null;
        } catch (Exception e) {
            logger.warn("LLM conflict response parse failed for anchor {} — falling back to text scan (response truncated to 200 chars: '{}'): {}",
                    anchor.id(),
                    raw != null ? raw.substring(0, Math.min(200, raw.length())) : "<null>",
                    e.getMessage());
            if (raw != null && raw.toLowerCase().contains("true")) {
                return new Conflict(anchor, incomingText, llmConfidence,
                                    "LLM indicated contradiction (fallback parse)");
            }
            return null;
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
