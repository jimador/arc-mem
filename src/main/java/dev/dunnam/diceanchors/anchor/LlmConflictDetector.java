package dev.dunnam.diceanchors.anchor;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dunnam.diceanchors.prompt.PromptTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects semantic contradictions between an incoming statement and existing anchors
 * using an LLM to evaluate factual consistency. Distinguishes genuine contradiction
 * from narrative progression.
 */
public class LlmConflictDetector implements ConflictDetector {

    private static final Logger logger = LoggerFactory.getLogger(LlmConflictDetector.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern CODE_FENCE = Pattern.compile("^```(?:json)?\\s*|\\s*```$");
    private static final double LLM_CONFLICT_CONFIDENCE = 0.9;

    private final ChatModel chatModel;
    private final String modelName;

    public LlmConflictDetector(ChatModel chatModel, String modelName) {
        this.chatModel = chatModel;
        this.modelName = modelName;
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

    private Conflict evaluatePair(String incomingText, Anchor anchor) {
        var promptText = PromptTemplates.render("prompts/dice/conflict-detection.jinja", java.util.Map.of(
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
                return new Conflict(anchor, incomingText, LLM_CONFLICT_CONFIDENCE, explanation);
            }
            return null;
        } catch (Exception e) {
            logger.debug("JSON parse failed for conflict response, using fallback: {}", e.getMessage());
            if (raw != null && raw.toLowerCase().contains("true")) {
                return new Conflict(anchor, incomingText, LLM_CONFLICT_CONFIDENCE,
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
