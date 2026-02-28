package dev.dunnam.diceanchors.chat;

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.annotation.MatryoshkaTools;
import com.embabel.dice.proposition.PropositionStatus;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.DemotionReason;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@MatryoshkaTools(name = "anchor-mutation-tools",
        description = "Tools for managing anchor state (pin, unpin, demote)",
        removeOnInvoke = false)
public record AnchorMutationTools(AnchorEngine engine, AnchorRepository repository, String contextId) {

    private static final Logger logger = LoggerFactory.getLogger(AnchorMutationTools.class);

    @LlmTool(description = """
            Pin an anchor so it cannot be evicted when the budget is exceeded. \
            Use for anchors representing critical, load-bearing facts that must remain \
            in context at all times. Only active anchors can be pinned. \
            Returns success=true if pinned, success=false with a reason message if not.""")
    public PinResult pinFact(
            @LlmTool.Param(description = "ID of the anchor to pin") String anchorId) {
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
    public PinResult unpinFact(
            @LlmTool.Param(description = "ID of the anchor to unpin") String anchorId) {
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

    @LlmTool(description = """
            Demote an anchor's authority by one level (e.g., RELIABLE→UNRELIABLE). \
            Use when new evidence contradicts or undermines an anchor's current authority. \
            PROVISIONAL anchors are archived (no lower authority exists). \
            For CANON anchors, a pending decanonization request is created instead of \
            immediate demotion when the canonization gate is enabled. \
            Returns a message describing the outcome or why demotion could not proceed.""")
    public String demoteAnchor(
            @LlmTool.Param(description = "ID of the anchor to demote") String anchorId) {
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
}
