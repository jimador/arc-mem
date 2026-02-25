package dev.dunnam.diceanchors.chat;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.annotation.MatryoshkaTools;
import com.embabel.dice.proposition.PropositionStatus;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.DemotionReason;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.persistence.PropositionNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * LLM-callable tools for querying and managing anchors during chat.
 * <p>
 * Exposes a curated subset of anchor operations as {@code @LlmTool} methods
 * so the LLM can actively query and manage its own knowledge. Read operations
 * are unrestricted; mutations are limited to safe pin/unpin and demotion operations
 * with guards against violating anchor invariants.
 * <p>
 * Invariant: CANON anchors cannot be unpinned (they are inherently preserved).
 * Invariant: Archived anchors cannot be pinned.
 * Invariant: CANON demotion is gated — a pending decanonization request is created
 * rather than an immediate demotion when the canonization gate is enabled.
 */
@MatryoshkaTools(name = "anchor-tools", description = "Tools for querying and managing established facts (anchors)")
public record AnchorTools(AnchorEngine engine, AnchorRepository repository, String contextId) {

    private static final Logger logger = LoggerFactory.getLogger(AnchorTools.class);
    private static final int SEARCH_TOP_K = 10;
    private static final double SEARCH_THRESHOLD = 0.5;

    @LlmTool(description = """
            Search for established facts (anchors) by subject or keyword. \
            Returns anchors with their ID, text, rank (100–900), authority level \
            (PROVISIONAL < UNRELIABLE < RELIABLE < CANON), pinned status, and confidence. \
            Use this before asserting facts to verify what is already known. \
            Returns an empty list if no matching anchors exist.""")
    public List<AnchorSummary> queryFacts(String subject) {
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
                              .map(AnchorTools::toSummary)
                              .toList();
        logger.info("Tool result: listAnchors returned {} anchors", summaries.size());
        return summaries;
    }

    @LlmTool(description = """
            Pin an anchor so it cannot be evicted when the budget is exceeded. \
            Use for anchors representing critical, load-bearing facts that must remain \
            in context at all times. Only active anchors can be pinned. \
            Returns success=true if pinned, success=false with a reason message if not.""")
    public PinResult pinFact(String anchorId) {
        logger.info("LLM tool call: pinFact with anchorId={}", anchorId);
        var nodeOpt = repository.findPropositionNodeById(anchorId);
        if (nodeOpt.isEmpty()) {
            return fail("Anchor not found: " + anchorId);
        }
        var node = nodeOpt.get();
        if (!node.isAnchor()) {
            return fail("Not an anchor: " + anchorId);
        }
        if (node.getStatus() != PropositionStatus.ACTIVE) {
            return fail("Anchor is archived: " + anchorId);
        }
        repository.updatePinned(anchorId, true);
        return ok("Fact pinned successfully");
    }

    @LlmTool(description = """
            Unpin a previously pinned anchor, allowing it to be evicted by normal \
            budget enforcement when lower-priority anchors must be removed. \
            CANON anchors cannot be unpinned — they are inherently preserved regardless of budget. \
            Returns success=true if unpinned, success=false with a reason message if not.""")
    public PinResult unpinFact(String anchorId) {
        logger.info("LLM tool call: unpinFact with anchorId={}", anchorId);
        var nodeOpt = repository.findPropositionNodeById(anchorId);
        if (nodeOpt.isEmpty()) {
            return fail("Anchor not found: " + anchorId);
        }
        var node = nodeOpt.get();
        if (!node.isAnchor()) {
            return fail("Not an anchor: " + anchorId);
        }
        if (!node.isPinned()) {
            return fail("Anchor is not pinned: " + anchorId);
        }
        if (node.getAuthority() != null && Authority.valueOf(node.getAuthority()) == Authority.CANON) {
            return fail("Cannot unpin CANON anchor — CANON facts are inherently preserved");
        }
        repository.updatePinned(anchorId, false);
        return ok("Fact unpinned");
    }

    /**
     * Demote an anchor's authority one level (RELIABLE→UNRELIABLE→PROVISIONAL).
     * PROVISIONAL anchors are archived when demoted further.
     * CANON demotion routes through the canonization gate if enabled — a pending
     * decanonization request is created rather than an immediate demotion.
     */
    @LlmTool(description = """
            Demote an anchor's authority by one level (e.g., RELIABLE→UNRELIABLE). \
            Use when new evidence contradicts or undermines an anchor's current authority. \
            PROVISIONAL anchors are archived (no lower authority exists). \
            For CANON anchors, a pending decanonization request is created instead of \
            immediate demotion when the canonization gate is enabled. \
            Returns a message describing the outcome or why demotion could not proceed.""")
    public String demoteAnchor(String anchorId) {
        logger.info("LLM tool call: demoteAnchor with anchorId={}", anchorId);
        if (repository.findPropositionNodeById(anchorId).isEmpty()) {
            logger.info("Tool result: anchor not found: {}", anchorId);
            return "Anchor not found: " + anchorId;
        }
        engine.demote(anchorId, DemotionReason.MANUAL);
        logger.info("Tool result: demoted anchor {}", anchorId);
        return "Demoted anchor " + anchorId;
    }

    private PinResult fail(String message) {
        var result = new PinResult(false, message);
        logger.info("Tool result: {}", result);
        return result;
    }

    private PinResult ok(String message) {
        var result = new PinResult(true, message);
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
