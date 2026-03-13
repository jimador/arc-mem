package dev.arcmem.core.memory.engine;

import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.core.memory.model.Authority;
import dev.arcmem.core.memory.model.MemoryTier;
import dev.arcmem.core.memory.model.MemoryUnit;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.persistence.PropositionNode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Read-only queries against the memory unit store: context listing,
 * counting, temporal queries, and supersession chain traversal.
 */
@Service
public class MemoryUnitQueryService {

    private final MemoryUnitRepository repository;
    private final ArcMemProperties.UnitConfig config;

    MemoryUnitQueryService(MemoryUnitRepository repository, ArcMemProperties properties) {
        this.repository = repository;
        this.config = properties.unit();
    }

    List<MemoryUnit> inject(String contextId) {
        var nodes = repository.findActiveUnits(contextId);
        return nodes.stream()
                    .limit(config.budget())
                    .map(this::toUnit)
                    .toList();
    }

    List<MemoryUnit> findByContext(String contextId) {
        return repository.findActiveUnits(contextId).stream()
                         .map(this::toUnit)
                         .toList();
    }

    int activeCount(String contextId) {
        return repository.countActiveUnits(contextId);
    }

    List<String> findValidAt(String contextId, Instant pointInTime) {
        return repository.findValidAt(contextId, pointInTime);
    }

    List<String> findSupersessionChain(String unitId) {
        return repository.findSupersessionChain(unitId);
    }

    Optional<String> findPredecessor(String unitId) {
        return repository.findPredecessor(unitId);
    }

    Optional<String> findSuccessor(String unitId) {
        return repository.findSuccessor(unitId);
    }

    MemoryUnit toUnit(PropositionNode node) {
        var authority = Authority.valueOf(
                node.getAuthority() != null ? node.getAuthority() : Authority.PROVISIONAL.name());
        var tier = computeTier(node.getRank());
        var sourceIds = node.getSourceIds();
        return new MemoryUnit(
                node.getId(),
                node.getText(),
                node.getRank(),
                authority,
                node.isPinned(),
                node.getConfidence(),
                node.getReinforcementCount(),
                null,
                node.getImportance(),
                node.getDecay(),
                tier,
                sourceIds.isEmpty() ? null : sourceIds.getFirst()
        );
    }

    MemoryTier computeTier(int rank) {
        var tierConfig = config.tier();
        if (tierConfig == null) {
            return MemoryTier.WARM;
        }
        return MemoryTier.fromRank(rank, tierConfig.hotThreshold(), tierConfig.warmThreshold());
    }
}
