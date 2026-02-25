package dev.dunnam.diceanchors.chat;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.annotation.MatryoshkaTools;
import dev.dunnam.diceanchors.DiceAnchorsProperties.RetrievalConfig;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.assembly.RelevanceScorer;
import dev.dunnam.diceanchors.assembly.ScoredAnchor;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LLM-callable tool for retrieving anchors relevant to a specific topic or question.
 * <p>
 * Uses {@link RelevanceScorer} to blend LLM semantic scores with heuristic signals
 * (authority, tier, confidence), then applies a quality gate ({@code minRelevance})
 * and top-k limit before returning results.
 * <p>
 * Registered conditionally in {@code ChatActions} when retrieval mode is
 * {@code HYBRID} or {@code TOOL}.
 * <p>
 * Tracks cumulative tool call count per instance via an {@link AtomicInteger},
 * emitted as the {@code retrieval.tool_call_count} OTEL span attribute on each call.
 */
@MatryoshkaTools(name = "anchor-retrieval",
        description = "Tools for retrieving relevant established facts by topic or question")
public record AnchorRetrievalTools(
        AnchorEngine engine,
        RelevanceScorer scorer,
        String contextId,
        RetrievalConfig config,
        AtomicInteger toolCallCounter
) {
    public AnchorRetrievalTools(AnchorEngine engine, RelevanceScorer scorer,
                                String contextId, RetrievalConfig config) {
        this(engine, scorer, contextId, config, new AtomicInteger(0));
    }

    private static final Logger logger = LoggerFactory.getLogger(AnchorRetrievalTools.class);

    @LlmTool(description = """
            Retrieve established facts (anchors) most relevant to a specific \
            topic or question. Returns anchors scored and ranked by relevance to your query. \
            Use this when you need grounding on a specific topic that may not be in your \
            baseline context. Each result includes the fact text, authority level, memory tier, \
            and a relevance score (0.0-1.0).""")
    public List<ScoredAnchor> retrieveAnchors(String query) {
        logger.info("LLM tool call: retrieveAnchors with query={}", query);
        var anchors = engine.inject(contextId);

        if (anchors.isEmpty()) {
            logger.info("Tool result: no anchors available for retrieval");
            return List.of();
        }

        var topK = config != null ? config.toolTopK() : 5;
        var minRelevance = config != null ? config.minRelevance() : 0.0;

        // Use LLM-based relevance scoring
        var scored = scorer.scoreByRelevance(query, anchors, config);

        // Apply quality gate and top-k
        var results = scored.stream()
                            .filter(sa -> sa.relevanceScore() >= minRelevance)
                            .limit(topK)
                            .toList();

        // Track cumulative tool call count via OTEL span attribute
        var callCount = toolCallCounter.incrementAndGet();
        var span = Span.current();
        span.setAttribute("retrieval.tool_call_count", callCount);

        logger.info("Tool result: retrieveAnchors returned {} results (top-k={}, minRelevance={})",
                    results.size(), topK, minRelevance);
        return results;
    }
}
