package dev.arcmem.simulator.chat;
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

import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.annotation.MatryoshkaTools;
import com.embabel.dice.proposition.PropositionStatus;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@MatryoshkaTools(name = "unit-mutation-tools",
        description = "Tools for managing unit state (pin, unpin, demote)",
        removeOnInvoke = false)
public record MemoryUnitMutationTools(ArcMemEngine engine, MemoryUnitRepository repository, String contextId) {

    private static final Logger logger = LoggerFactory.getLogger(MemoryUnitMutationTools.class);

    @LlmTool(description = """
            Pin an unit so it cannot be evicted when the budget is exceeded. \
            Use for units representing critical, load-bearing facts that must remain \
            in context at all times. Only active units can be pinned. \
            Returns success=true if pinned, success=false with a reason message if not.""")
    public PinResult pinFact(
            @LlmTool.Param(description = "ID of the unit to pin") String unitId) {
        logger.info("LLM tool call: pinFact with unitId={}", unitId);
        var nodeOpt = repository.findPropositionNodeById(unitId);
        if (nodeOpt.isEmpty()) {
            return fail("MemoryUnit not found: " + unitId);
        }
        var node = nodeOpt.get();
        if (!node.isUnit()) {
            return fail("Not an unit: " + unitId);
        }
        if (node.getStatus() != PropositionStatus.ACTIVE) {
            return fail("MemoryUnit is archived: " + unitId);
        }
        repository.updatePinned(unitId, true);
        return ok("Fact pinned successfully");
    }

    @LlmTool(description = """
            Unpin a previously pinned unit, allowing it to be evicted by normal \
            budget enforcement when lower-priority units must be removed. \
            CANON units cannot be unpinned — they are inherently preserved regardless of budget. \
            Returns success=true if unpinned, success=false with a reason message if not.""")
    public PinResult unpinFact(
            @LlmTool.Param(description = "ID of the unit to unpin") String unitId) {
        logger.info("LLM tool call: unpinFact with unitId={}", unitId);
        var nodeOpt = repository.findPropositionNodeById(unitId);
        if (nodeOpt.isEmpty()) {
            return fail("MemoryUnit not found: " + unitId);
        }
        var node = nodeOpt.get();
        if (!node.isUnit()) {
            return fail("Not an unit: " + unitId);
        }
        if (!node.isPinned()) {
            return fail("MemoryUnit is not pinned: " + unitId);
        }
        if (node.getAuthority() != null && Authority.valueOf(node.getAuthority()) == Authority.CANON) {
            return fail("Cannot unpin CANON unit — CANON facts are inherently preserved");
        }
        repository.updatePinned(unitId, false);
        return ok("Fact unpinned");
    }

    @LlmTool(description = """
            Demote an unit's authority by one level (e.g., RELIABLE→UNRELIABLE). \
            Use when new evidence contradicts or undermines an unit's current authority. \
            PROVISIONAL units are archived (no lower authority exists). \
            For CANON units, a pending decanonization request is created instead of \
            immediate demotion when the canonization gate is enabled. \
            Returns a message describing the outcome or why demotion could not proceed.""")
    public String demoteUnit(
            @LlmTool.Param(description = "ID of the unit to demote") String unitId) {
        logger.info("LLM tool call: demoteUnit with unitId={}", unitId);
        if (repository.findPropositionNodeById(unitId).isEmpty()) {
            logger.info("Tool result: unit not found: {}", unitId);
            return "MemoryUnit not found: " + unitId;
        }
        engine.demote(unitId, DemotionReason.MANUAL);
        logger.info("Tool result: demoted unit {}", unitId);
        return "Demoted unit " + unitId;
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
