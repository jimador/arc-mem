package dev.dunnam.diceanchors.chat;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.annotation.MatryoshkaTools;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * LLM-callable tools for querying and managing anchors during chat.
 * <p>
 * Exposes a curated subset of anchor operations as {@code @LlmTool} methods
 * so the LLM can actively query and manage its own knowledge. Read operations
 * are unrestricted; mutations are limited to safe pin/unpin operations with
 * guards against violating anchor invariants.
 * <p>
 * Invariant: CANON anchors cannot be unpinned (they are inherently preserved).
 * Invariant: Archived anchors cannot be pinned.
 */
@MatryoshkaTools(name = "anchor-tools", description = "Tools for querying and managing established facts (anchors)")
public record AnchorTools(AnchorEngine engine, AnchorRepository repository, String contextId) {

    private static final Logger logger = LoggerFactory.getLogger(AnchorTools.class);
    private static final int SEARCH_TOP_K = 10;
    private static final double SEARCH_THRESHOLD = 0.5;

    @LlmTool(description = "Query established facts about a subject. Returns matching anchors with rank and authority.")
    public List<AnchorSummary> queryFacts(String subject) {
        logger.info("LLM tool call: queryFacts with subject={}", subject);
        var results = repository.semanticSearch(subject, contextId, SEARCH_TOP_K, SEARCH_THRESHOLD);
        var summaries = results.stream()
                .map(scored -> {
                    var node = repository.findPropositionNodeById(scored.id());
                    if (node == null || !node.isAnchor()) {
                        return null;
                    }
                    return new AnchorSummary(
                            node.getId(),
                            node.getText(),
                            node.getRank(),
                            node.getAuthority() != null ? node.getAuthority() : Authority.PROVISIONAL.name(),
                            node.isPinned(),
                            node.getConfidence()
                    );
                })
                .filter(s -> s != null)
                .toList();
        logger.info("Tool result: queryFacts returned {} anchors", summaries.size());
        return summaries;
    }

    @LlmTool(description = "Get all currently active anchors with their rank, authority, and confidence.")
    public List<AnchorSummary> listAnchors() {
        logger.info("LLM tool call: listAnchors for context={}", contextId);
        var anchors = engine.inject(contextId);
        var summaries = anchors.stream()
                .map(AnchorTools::toSummary)
                .toList();
        logger.info("Tool result: listAnchors returned {} anchors", summaries.size());
        return summaries;
    }

    @LlmTool(description = "Pin an important fact so it cannot be evicted by budget enforcement.")
    public PinResult pinFact(String anchorId) {
        logger.info("LLM tool call: pinFact with anchorId={}", anchorId);
        var node = repository.findPropositionNodeById(anchorId);
        if (node == null) {
            var result = new PinResult(false, "Anchor not found: " + anchorId);
            logger.info("Tool result: {}", result);
            return result;
        }
        if (!node.isAnchor()) {
            var result = new PinResult(false, "Not an anchor: " + anchorId);
            logger.info("Tool result: {}", result);
            return result;
        }
        if (node.getStatus() != com.embabel.dice.proposition.PropositionStatus.ACTIVE) {
            var result = new PinResult(false, "Anchor is archived: " + anchorId);
            logger.info("Tool result: {}", result);
            return result;
        }
        repository.updatePinned(anchorId, true);
        var result = new PinResult(true, "Fact pinned successfully");
        logger.info("Tool result: {}", result);
        return result;
    }

    @LlmTool(description = "Unpin a previously pinned fact, allowing normal eviction rules.")
    public PinResult unpinFact(String anchorId) {
        logger.info("LLM tool call: unpinFact with anchorId={}", anchorId);
        var node = repository.findPropositionNodeById(anchorId);
        if (node == null) {
            var result = new PinResult(false, "Anchor not found: " + anchorId);
            logger.info("Tool result: {}", result);
            return result;
        }
        if (!node.isAnchor()) {
            var result = new PinResult(false, "Not an anchor: " + anchorId);
            logger.info("Tool result: {}", result);
            return result;
        }
        if (!node.isPinned()) {
            var result = new PinResult(false, "Anchor is not pinned: " + anchorId);
            logger.info("Tool result: {}", result);
            return result;
        }
        if (node.getAuthority() != null && Authority.valueOf(node.getAuthority()) == Authority.CANON) {
            var result = new PinResult(false, "Cannot unpin CANON anchor — CANON facts are inherently preserved");
            logger.info("Tool result: {}", result);
            return result;
        }
        repository.updatePinned(anchorId, false);
        var result = new PinResult(true, "Fact unpinned");
        logger.info("Tool result: {}", result);
        return result;
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
