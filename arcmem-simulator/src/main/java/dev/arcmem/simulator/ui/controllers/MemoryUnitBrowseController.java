package dev.arcmem.simulator.ui.controllers;
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

import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.persistence.PropositionNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for browsing units within a simulation context.
 * All data is scoped to a specific contextId (typically a simulation run ID).
 */
@RestController
@RequestMapping("/api/units")
public class MemoryUnitBrowseController {

    private static final Logger logger = LoggerFactory.getLogger(MemoryUnitBrowseController.class);

    private final ArcMemEngine arcMemEngine;
    private final MemoryUnitRepository contextUnitRepository;

    public MemoryUnitBrowseController(ArcMemEngine arcMemEngine, MemoryUnitRepository contextUnitRepository) {
        this.arcMemEngine = arcMemEngine;
        this.contextUnitRepository = contextUnitRepository;
    }

    /**
     * List active units for a context, ranked by priority.
     */
    @GetMapping("/{contextId}")
    public List<UnitDto> listUnits(@PathVariable String contextId) {
        var units = arcMemEngine.inject(contextId);
        return units.stream()
                      .map(MemoryUnitBrowseController::toDto)
                      .toList();
    }

    /**
     * Get a single unit by its proposition ID within a context.
     */
    @GetMapping("/{contextId}/{id}")
    public ResponseEntity<UnitDto> getUnit(
            @PathVariable String contextId,
            @PathVariable String id) {
        var nodeOpt = contextUnitRepository.findPropositionNodeById(id);
        if (nodeOpt.isEmpty() || !nodeOpt.get().isUnit() || !contextId.equals(nodeOpt.get().getContextId())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toDto(nodeOpt.get()));
    }

    /**
     * Search units by authority level within a context.
     */
    @GetMapping("/{contextId}/search")
    public List<UnitDto> searchByAuthority(
            @PathVariable String contextId,
            @RequestParam String authority) {
        var level = Authority.valueOf(authority);
        var allUnits = arcMemEngine.inject(contextId);
        return allUnits.stream()
                         .filter(a -> a.authority() == level)
                         .map(MemoryUnitBrowseController::toDto)
                         .toList();
    }

    /**
     * Get the history of an unit (reinforcement count, created/revised timestamps).
     */
    @GetMapping("/{contextId}/{id}/history")
    public ResponseEntity<UnitHistoryDto> getHistory(
            @PathVariable String contextId,
            @PathVariable String id) {
        var nodeOpt = contextUnitRepository.findPropositionNodeById(id);
        if (nodeOpt.isEmpty() || !nodeOpt.get().isUnit() || !contextId.equals(nodeOpt.get().getContextId())) {
            return ResponseEntity.notFound().build();
        }
        var node = nodeOpt.get();
        var history = new UnitHistoryDto(
                node.getId(),
                node.getText(),
                node.getRank(),
                node.getAuthority(),
                node.isPinned(),
                node.getReinforcementCount(),
                node.getCreated() != null ? node.getCreated().toString() : null,
                node.getRevised() != null ? node.getRevised().toString() : null,
                node.getLastReinforced() != null ? node.getLastReinforced().toString() : null,
                node.getDecayType()
        );
        return ResponseEntity.ok(history);
    }

    /**
     * MemoryUnit summary for list/search responses.
     */
    public record UnitDto(
            String id,
            String text,
            int rank,
            String authority,
            boolean pinned,
            String decayType,
            int reinforcementCount
    ) {}

    public record UnitHistoryDto(
            String id,
            String text,
            int rank,
            String authority,
            boolean pinned,
            int reinforcementCount,
            String created,
            String revised,
            String lastReinforced,
            String decayType
    ) {}

    private static UnitDto toDto(MemoryUnit unit) {
        return new UnitDto(
                unit.id(),
                unit.text(),
                unit.rank(),
                unit.authority().name(),
                unit.pinned(),
                null,
                unit.reinforcementCount()
        );
    }

    private static UnitDto toDto(PropositionNode node) {
        return new UnitDto(
                node.getId(),
                node.getText(),
                node.getRank(),
                node.getAuthority(),
                node.isPinned(),
                node.getDecayType(),
                node.getReinforcementCount()
        );
    }
}
