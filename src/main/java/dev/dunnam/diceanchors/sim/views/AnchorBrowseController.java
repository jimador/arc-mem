package dev.dunnam.diceanchors.sim.views;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.persistence.PropositionNode;
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
 * REST endpoints for browsing anchors within a simulation context.
 * All data is scoped to a specific contextId (typically a simulation run ID).
 */
@RestController
@RequestMapping("/api/anchors")
public class AnchorBrowseController {

    private static final Logger logger = LoggerFactory.getLogger(AnchorBrowseController.class);

    private final AnchorEngine anchorEngine;
    private final AnchorRepository anchorRepository;

    public AnchorBrowseController(AnchorEngine anchorEngine, AnchorRepository anchorRepository) {
        this.anchorEngine = anchorEngine;
        this.anchorRepository = anchorRepository;
    }

    /**
     * List active anchors for a context, ranked by priority.
     */
    @GetMapping("/{contextId}")
    public List<AnchorDto> listAnchors(@PathVariable String contextId) {
        var anchors = anchorEngine.inject(contextId);
        return anchors.stream()
                      .map(AnchorBrowseController::toDto)
                      .toList();
    }

    /**
     * Get a single anchor by its proposition ID within a context.
     */
    @GetMapping("/{contextId}/{id}")
    public ResponseEntity<AnchorDto> getAnchor(
            @PathVariable String contextId,
            @PathVariable String id) {
        var nodeOpt = anchorRepository.findPropositionNodeById(id);
        if (nodeOpt.isEmpty() || !nodeOpt.get().isAnchor() || !contextId.equals(nodeOpt.get().getContextId())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toDto(nodeOpt.get()));
    }

    /**
     * Search anchors by authority level within a context.
     */
    @GetMapping("/{contextId}/search")
    public List<AnchorDto> searchByAuthority(
            @PathVariable String contextId,
            @RequestParam String authority) {
        var level = Authority.valueOf(authority);
        var allAnchors = anchorEngine.inject(contextId);
        return allAnchors.stream()
                         .filter(a -> a.authority() == level)
                         .map(AnchorBrowseController::toDto)
                         .toList();
    }

    /**
     * Get the history of an anchor (reinforcement count, created/revised timestamps).
     */
    @GetMapping("/{contextId}/{id}/history")
    public ResponseEntity<AnchorHistoryDto> getHistory(
            @PathVariable String contextId,
            @PathVariable String id) {
        var nodeOpt = anchorRepository.findPropositionNodeById(id);
        if (nodeOpt.isEmpty() || !nodeOpt.get().isAnchor() || !contextId.equals(nodeOpt.get().getContextId())) {
            return ResponseEntity.notFound().build();
        }
        var node = nodeOpt.get();
        var history = new AnchorHistoryDto(
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
     * Anchor summary for list/search responses.
     */
    public record AnchorDto(
            String id,
            String text,
            int rank,
            String authority,
            boolean pinned,
            String decayType,
            int reinforcementCount
    ) {}

    public record AnchorHistoryDto(
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

    private static AnchorDto toDto(Anchor anchor) {
        return new AnchorDto(
                anchor.id(),
                anchor.text(),
                anchor.rank(),
                anchor.authority().name(),
                anchor.pinned(),
                null,
                anchor.reinforcementCount()
        );
    }

    private static AnchorDto toDto(PropositionNode node) {
        return new AnchorDto(
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
