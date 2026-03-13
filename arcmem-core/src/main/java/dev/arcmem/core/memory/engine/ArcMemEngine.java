package dev.arcmem.core.memory.engine;

import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.core.memory.budget.BudgetStrategy;
import dev.arcmem.core.memory.canon.DemotionReason;
import dev.arcmem.core.memory.canon.InvariantEvaluation;
import dev.arcmem.core.memory.canon.InvariantEvaluator;
import dev.arcmem.core.memory.canon.InvariantStrength;
import dev.arcmem.core.memory.canon.ProposedAction;
import dev.arcmem.core.memory.conflict.ConflictDetector;
import dev.arcmem.core.memory.conflict.ConflictResolver;
import dev.arcmem.core.memory.event.ArchiveReason;
import dev.arcmem.core.memory.event.MemoryUnitLifecycleEvent;
import dev.arcmem.core.memory.model.Authority;
import dev.arcmem.core.memory.model.MemoryUnit;
import dev.arcmem.core.memory.trust.TrustAuditRecord;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.persistence.PropositionNode;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Facade coordinating all unit lifecycle operations: promotion, reinforcement,
 * decay, demotion, archiving, conflict detection, and context injection.
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li><strong>A1</strong>: Active unit count never exceeds budget after
 *       {@link #promote} returns.</li>
 *   <li><strong>A2</strong>: Rank is always clamped to
 *       [{@link MemoryUnit#MIN_RANK}, {@link MemoryUnit#MAX_RANK}].</li>
 *   <li><strong>A3a</strong>: CANON is never assigned by automatic promotion. Only
 *       explicit action (seed, manual, or approved canonization request) can set CANON.</li>
 *   <li><strong>A3b</strong>: CANON units are immune to automatic demotion. When the
 *       canonization gate is enabled, demotion of CANON creates a pending decanonization
 *       request instead of executing immediately.</li>
 *   <li><strong>A3c</strong>: Automatic demotion applies to RELIABLE → UNRELIABLE →
 *       PROVISIONAL via decay or trust re-evaluation.</li>
 *   <li><strong>A3d</strong>: Pinned units are immune to automatic demotion. Explicit
 *       demotion still works.</li>
 *   <li><strong>A3e</strong>: All authority transitions (both directions) publish
 *       {@link MemoryUnitLifecycleEvent.AuthorityChanged} lifecycle events.</li>
 * </ul>
 *
 * <p>Delegates lifecycle mutations to {@link MemoryUnitLifecycleService} and
 * read-only queries to {@link MemoryUnitQueryService}.
 */
@Service
public class ArcMemEngine {

    private static final Logger logger = LoggerFactory.getLogger(ArcMemEngine.class);

    private final MemoryUnitLifecycleService lifecycleService;
    private final MemoryUnitQueryService queryService;
    private final ConflictDetector conflictDetector;
    private final ConflictResolver conflictResolver;
    private final MemoryUnitRepository repository;
    private final InvariantEvaluator invariantEvaluator;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean lifecycleEventsEnabled;

    public ArcMemEngine(
            MemoryUnitLifecycleService lifecycleService,
            MemoryUnitQueryService queryService,
            ConflictDetector conflictDetector,
            ConflictResolver conflictResolver,
            MemoryUnitRepository repository,
            InvariantEvaluator invariantEvaluator,
            ApplicationEventPublisher eventPublisher,
            ArcMemProperties properties) {
        this.lifecycleService = lifecycleService;
        this.queryService = queryService;
        this.conflictDetector = conflictDetector;
        this.conflictResolver = conflictResolver;
        this.repository = repository;
        this.invariantEvaluator = invariantEvaluator;
        this.eventPublisher = eventPublisher;
        this.lifecycleEventsEnabled = properties.unit().lifecycleEventsEnabled();
    }

    public List<MemoryUnit> inject(String contextId) {
        return queryService.inject(contextId);
    }

    public void promote(String propositionId, int initialRank) {
        lifecycleService.promote(propositionId, initialRank);
    }

    public void promote(String propositionId, int initialRank, Authority authorityCeiling) {
        lifecycleService.promote(propositionId, initialRank, authorityCeiling);
    }

    public void reinforce(String unitId) {
        lifecycleService.reinforce(unitId);
    }

    public void reinforce(String unitId, boolean rankMutationEnabled, boolean authorityPromotionEnabled) {
        lifecycleService.reinforce(unitId, rankMutationEnabled, authorityPromotionEnabled);
    }

    public void demote(String unitId, DemotionReason reason) {
        lifecycleService.demote(unitId, reason);
    }

    public void applyDecay(String unitId, int newRank) {
        lifecycleService.applyDecay(unitId, newRank);
    }

    public void archive(String unitId, ArchiveReason reason) {
        lifecycleService.archive(unitId, reason);
    }

    public void archive(String unitId, ArchiveReason reason, @Nullable String successorId) {
        lifecycleService.archive(unitId, reason, successorId);
    }

    public void supersede(String predecessorId, String successorId, ArchiveReason reason) {
        lifecycleService.supersede(predecessorId, successorId, reason);
    }

    public void reEvaluateTrust(String unitId) {
        lifecycleService.reEvaluateTrust(unitId);
    }

    public List<TrustAuditRecord> trustAuditLog(@Nullable String contextId) {
        return lifecycleService.trustAuditLog(contextId);
    }

    public void clearTrustAuditLog(@Nullable String contextId) {
        lifecycleService.clearTrustAuditLog(contextId);
    }

    public List<ConflictDetector.Conflict> detectConflicts(String contextId, String incomingText) {
        var units = inject(contextId);
        var conflicts = conflictDetector.detect(incomingText, units);
        if (!conflicts.isEmpty()) {
            var existingIds = conflicts.stream()
                    .map(ConflictDetector.Conflict::existing)
                    .filter(existing -> existing != null)
                    .map(MemoryUnit::id)
                    .toList();
            publish(MemoryUnitLifecycleEvent.conflictDetected(
                    this, contextId, incomingText,
                    conflicts.size(),
                    existingIds));
        }
        return conflicts;
    }

    public Map<String, List<ConflictDetector.Conflict>> batchDetectConflicts(
            String contextId, List<String> incomingTexts) {
        var units = inject(contextId);
        if (units.isEmpty() || incomingTexts.isEmpty()) {
            return incomingTexts.stream()
                    .collect(Collectors.toMap(t -> t, t -> List.of()));
        }
        return conflictDetector.batchDetect(incomingTexts, units);
    }

    public ConflictResolver.Resolution resolveConflict(ConflictDetector.Conflict conflict) {
        if (conflict.existing() == null) {
            logger.warn("Received degraded conflict placeholder without existing unit (incoming='{}')",
                    conflict.incomingText());
            var resolution = conflictResolver.resolve(conflict);
            publish(MemoryUnitLifecycleEvent.conflictResolved(
                    this, "unknown", "unknown", resolution));
            return resolution;
        }

        var resolution = conflictResolver.resolve(conflict);
        var contextId = repository.findPropositionNodeById(conflict.existing().id())
                .map(PropositionNode::getContextId)
                .orElse("unknown");
        publish(MemoryUnitLifecycleEvent.conflictResolved(
                this, contextId, conflict.existing().id(), resolution));
        return resolution;
    }

    public void registerBudgetStrategy(String contextId, BudgetStrategy strategy) {
        lifecycleService.registerBudgetStrategy(contextId, strategy);
    }

    public void clearBudgetStrategy(String contextId) {
        lifecycleService.clearBudgetStrategy(contextId);
    }

    public List<MemoryUnit> findByContext(String contextId) {
        return queryService.findByContext(contextId);
    }

    public int activeCount(String contextId) {
        return queryService.activeCount(contextId);
    }

    public List<String> findValidAt(String contextId, Instant pointInTime) {
        return queryService.findValidAt(contextId, pointInTime);
    }

    public List<String> findSupersessionChain(String unitId) {
        return queryService.findSupersessionChain(unitId);
    }

    public Optional<String> findPredecessor(String unitId) {
        return queryService.findPredecessor(unitId);
    }

    public Optional<String> findSuccessor(String unitId) {
        return queryService.findSuccessor(unitId);
    }

    public InvariantEvaluation evaluateInvariantSummary(String contextId, List<MemoryUnit> units) {
        var eval = invariantEvaluator.evaluate(contextId, ProposedAction.EVICT, units, null);
        return eval != null ? eval : new InvariantEvaluation(List.of(), 0);
    }

    MemoryUnit toUnit(PropositionNode node) {
        return queryService.toUnit(node);
    }

    private void publish(MemoryUnitLifecycleEvent event) {
        if (!lifecycleEventsEnabled) {
            return;
        }
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            logger.warn("Failed to publish lifecycle event {}: {}",
                    event.getClass().getSimpleName(), e.getMessage());
        }
    }
}
