package dev.dunnam.diceanchors.assembly;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dunnam.diceanchors.DiceAnchorsProperties.RetrievalConfig;
import dev.dunnam.diceanchors.DiceAnchorsProperties.ScoringConfig;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.MemoryTier;
import dev.dunnam.diceanchors.prompt.PromptPathConstants;
import dev.dunnam.diceanchors.prompt.PromptTemplates;
import dev.dunnam.diceanchors.sim.engine.LlmCallService;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Computes relevance scores for anchors using heuristic signals (authority, tier, confidence)
 * and optionally LLM-based semantic scoring.
 * <p>
 * When a {@link ChatModel} is available, {@link #scoreByRelevance} blends LLM semantic scores
 * (60%) with heuristic scores (40%). When no ChatModel is provided, falls back to pure
 * heuristic scoring.
 */
public class RelevanceScorer {

    private static final Logger logger = LoggerFactory.getLogger(RelevanceScorer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern CODE_FENCE = Pattern.compile("^```(?:json)?\\s*|\\s*```$");
    private static final double LLM_WEIGHT = 0.6;
    private static final double HEURISTIC_WEIGHT = 0.4;

    private final @Nullable ChatModel chatModel;
    private final @Nullable String modelName;
    private final @Nullable LlmCallService llmCallService;

    public RelevanceScorer(@Nullable ChatModel chatModel, @Nullable String modelName,
                           @Nullable LlmCallService llmCallService) {
        this.chatModel = chatModel;
        this.modelName = modelName;
        this.llmCallService = llmCallService;
    }

    public RelevanceScorer() {
        this(null, null, null);
    }

    /**
     * Computes a heuristic relevance score for an anchor based on authority, tier, and confidence.
     *
     * @param scoring weight configuration; if null, uses multiplicative fallback
     * @return score in [0.0, 1.0]
     */
    public double computeHeuristicScore(Anchor anchor, @Nullable ScoringConfig scoring) {
        var authorityVal = authorityValue(anchor.authority());
        var tierVal = tierValue(anchor.memoryTier());
        var confidence = anchor.confidence();
        if (scoring == null) {
            return authorityVal * tierVal * confidence;
        }
        return (scoring.authorityWeight() * authorityVal)
             + (scoring.tierWeight() * tierVal)
             + (scoring.confidenceWeight() * confidence);
    }

    /**
     * Scores and ranks anchors using heuristic signals only.
     *
     * @param scoring weight configuration
     * @return anchors sorted by descending relevance score
     */
    public List<ScoredAnchor> scoreAndRank(List<Anchor> anchors, @Nullable ScoringConfig scoring) {
        return anchors.stream()
                .map(a -> new ScoredAnchor(
                        a.id(), a.text(), a.rank(), a.authority(),
                        a.confidence(), a.memoryTier(),
                        computeHeuristicScore(a, scoring)))
                .sorted(Comparator.comparingDouble(ScoredAnchor::relevanceScore).reversed())
                .toList();
    }

    /**
     * Scores anchors by relevance to a query, blending LLM semantic scores with heuristic scores.
     * Falls back to heuristic-only scoring if ChatModel is unavailable.
     *
     * @param query  the user query or conversational context
     * @param config retrieval configuration containing scoring weights
     * @return anchors sorted by descending blended relevance score
     */
    public List<ScoredAnchor> scoreByRelevance(String query, List<Anchor> anchors,
                                                RetrievalConfig config) {
        var scoring = config != null ? config.scoring() : null;

        if (chatModel == null || llmCallService == null) {
            logger.warn("ChatModel or LlmCallService unavailable — falling back to heuristic-only scoring");
            return scoreAndRank(anchors, scoring);
        }

        try {
            var llmScores = callLlmForScores(query, anchors);
            return blendScores(anchors, llmScores, scoring);
        } catch (Exception e) {
            logger.warn("LLM relevance scoring failed, falling back to heuristic-only: {}", e.getMessage());
            return scoreAndRank(anchors, scoring);
        }
    }

    private Map<String, Double> callLlmForScores(String query, List<Anchor> anchors) {
        var anchorEntries = anchors.stream()
                .map(a -> Map.<String, Object>of("id", a.id(), "text", a.text()))
                .toList();

        var systemPrompt = "You are a relevance scoring engine. Rate how relevant each anchor is to the query.";
        var userPrompt = PromptTemplates.render(PromptPathConstants.RELEVANCE_SCORING, Map.of(
                "query", query,
                "anchors", anchorEntries));

        var responseText = llmCallService.callBatched(systemPrompt, userPrompt);
        return parseLlmScoreResponse(responseText);
    }

    private Map<String, Double> parseLlmScoreResponse(String responseText) {
        var scores = new HashMap<String, Double>();
        try {
            var json = stripCodeFences(responseText);
            var node = MAPPER.readTree(json);
            var scoresNode = node.has("scores") ? node.get("scores") : node;
            var fields = scoresNode.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                var score = entry.getValue().asDouble(0.0);
                scores.put(entry.getKey(), Math.max(0.0, Math.min(1.0, score)));
            }
        } catch (Exception e) {
            logger.warn("Failed to parse LLM relevance scores (response truncated to 200 chars: '{}'): {}",
                    responseText != null ? responseText.substring(0, Math.min(200, responseText.length())) : "<null>",
                    e.getMessage());
        }
        return scores;
    }

    private List<ScoredAnchor> blendScores(List<Anchor> anchors, Map<String, Double> llmScores,
                                            @Nullable ScoringConfig scoring) {
        return anchors.stream()
                .map(a -> {
                    var heuristic = computeHeuristicScore(a, scoring);
                    var llmScore = llmScores.getOrDefault(a.id(), heuristic);
                    var blended = (LLM_WEIGHT * llmScore) + (HEURISTIC_WEIGHT * heuristic);
                    return new ScoredAnchor(
                            a.id(), a.text(), a.rank(), a.authority(),
                            a.confidence(), a.memoryTier(), blended);
                })
                .sorted(Comparator.comparingDouble(ScoredAnchor::relevanceScore).reversed())
                .toList();
    }

    static double authorityValue(Authority authority) {
        return switch (authority) {
            case CANON -> 1.0;
            case RELIABLE -> 0.8;
            case UNRELIABLE -> 0.5;
            case PROVISIONAL -> 0.3;
        };
    }

    static double tierValue(MemoryTier tier) {
        return switch (tier) {
            case HOT -> 1.0;
            case WARM -> 0.7;
            case COLD -> 0.4;
        };
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
