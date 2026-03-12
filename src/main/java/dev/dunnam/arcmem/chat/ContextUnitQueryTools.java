package dev.dunnam.diceanchors.chat;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.annotation.MatryoshkaTools;
import dev.dunnam.diceanchors.DiceAnchorsProperties.RetrievalConfig;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.assembly.RelevanceScorer;
import dev.dunnam.diceanchors.assembly.ScoredAnchor;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.persistence.PropositionNode;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@MatryoshkaTools(name = "anchor-query-tools",
        description = "Read-only tools for querying established facts (anchors)",
        removeOnInvoke = false)
public record AnchorQueryTools(
        AnchorEngine engine,
        AnchorRepository repository,
        RelevanceScorer scorer,
        String contextId,
        RetrievalConfig config,
        AtomicInteger toolCallCounter
) {
    public AnchorQueryTools(AnchorEngine engine, AnchorRepository repository,
                            RelevanceScorer scorer, String contextId) {
        this(engine, repository, scorer, contextId, null, new AtomicInteger(0));
    }

    private static final Logger logger = LoggerFactory.getLogger(AnchorQueryTools.class);
    private static final int SEARCH_TOP_K = 10;
    private static final double SEARCH_THRESHOLD = 0.5;

    @LlmTool(description = """
            Search for established facts (anchors) by subject or keyword. \
            Returns anchors with their ID, text, rank (100–900), authority level \
            (PROVISIONAL < UNRELIABLE < RELIABLE < CANON), pinned status, and confidence. \
            Use this before asserting facts to verify what is already known. \
            Returns an empty list if no matching anchors exist.""")
    public List<AnchorSummary> queryFacts(
            @LlmTool.Param(description = "Subject or keyword to search for in established facts") String subject) {
        logger.info("LLM tool call: queryFacts with subject={}", subject);
        var summaries = repository.semanticSearch(subject, contextId, SEARCH_TOP_K, SEARCH_THRESHOLD)
                                  .stream()
                                  .map(scored -> repository.findPropositionNodeById(scored.id())
                                                           .filter(PropositionNode::isAnchor)
                                                           .map(node -> new AnchorSummary(
                                                                   node.getId(),
                                                                   node.getText(),
                                                                   node.getRank(),
                                                                   node.getAuthority() != null ? node.getAuthority() : Authority.PROVISIONAL.name(),
                                                                   node.isPinned(),
                                                                   node.getConfidence()
                                                           ))
                                                           .orElse(null))
                                  .filter(Objects::nonNull)
                                  .toList();
        logger.info("Tool result: queryFacts returned {} anchors", summaries.size());
        return summaries;
    }

    @LlmTool(description = """
            List all currently active anchors for the current conversation context. \
            Returns each anchor's ID, text, rank (100–900), authority \
            (PROVISIONAL < UNRELIABLE < RELIABLE < CANON), pinned status, and confidence. \
            Anchors are ordered highest-rank first. Use this to review the full known-facts \
            baseline before making assertions or when auditing context consistency.""")
    public List<AnchorSummary> listAnchors() {
        logger.info("LLM tool call: listAnchors for context={}", contextId);
        var summaries = engine.inject(contextId).stream()
                              .map(AnchorQueryTools::toSummary)
                              .toList();
        logger.info("Tool result: listAnchors returned {} anchors", summaries.size());
        return summaries;
    }

    @LlmTool(description = """
            Retrieve established facts (anchors) most relevant to a specific \
            topic or question. Returns anchors scored and ranked by relevance to your query. \
            Use this when you need grounding on a specific topic that may not be in your \
            baseline context. Each result includes the fact text, authority level, memory tier, \
            and a relevance score (0.0-1.0).""")
    public List<ScoredAnchor> retrieveAnchors(
            @LlmTool.Param(description = "Topic or question to find relevant anchors for") String query) {
        logger.info("LLM tool call: retrieveAnchors with query={}", query);
        var anchors = engine.inject(contextId);

        if (anchors.isEmpty()) {
            logger.info("Tool result: no anchors available for retrieval");
            return List.of();
        }

        var topK = config != null ? config.toolTopK() : 5;
        var minRelevance = config != null ? config.minRelevance() : 0.0;

        var scored = scorer.scoreByRelevance(query, anchors, config);

        var results = scored.stream()
                            .filter(sa -> sa.relevanceScore() >= minRelevance)
                            .limit(topK)
                            .toList();

        var callCount = toolCallCounter.incrementAndGet();
        Span.current().setAttribute("retrieval.tool_call_count", callCount);

        logger.info("Tool result: retrieveAnchors returned {} results (top-k={}, minRelevance={})",
                    results.size(), topK, minRelevance);
        return results;
    }

    private static AnchorSummary toSummary(Anchor anchor) {
        return new AnchorSummary(
                anchor.id(),
                anchor.text(),
                anchor.rank(),
                anchor.authority().name(),
                anchor.pinned(),
                anchor.confidence()
        );
    }
}
