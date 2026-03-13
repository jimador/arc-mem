package dev.arcmem.core.memory.engine;

import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.core.memory.budget.BudgetStrategy;
import dev.arcmem.core.memory.canon.CanonizationGate;
import dev.arcmem.core.memory.canon.DemotionReason;
import dev.arcmem.core.memory.canon.InvariantEvaluation;
import dev.arcmem.core.memory.canon.InvariantEvaluator;
import dev.arcmem.core.memory.canon.InvariantStrength;
import dev.arcmem.core.memory.canon.ProposedAction;
import dev.arcmem.core.memory.conflict.AuthorityChangeDirection;
import dev.arcmem.core.memory.conflict.ConflictDetector;
import dev.arcmem.core.memory.conflict.ConflictResolver;
import dev.arcmem.core.memory.maintenance.DecayPolicy;
import dev.arcmem.core.memory.mutation.ReinforcementPolicy;
import dev.arcmem.core.memory.model.Authority;
import dev.arcmem.core.memory.model.MemoryTier;
import dev.arcmem.core.memory.model.MemoryUnit;
import dev.arcmem.core.memory.event.ArchiveReason;
import dev.arcmem.core.memory.event.MemoryUnitLifecycleEvent;
import dev.arcmem.core.memory.event.SupersessionReason;
import dev.arcmem.core.memory.trust.TrustAuditRecord;
import dev.arcmem.core.memory.trust.TrustPipeline;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.persistence.PropositionNode;
import com.embabel.dice.proposition.PropositionStatus;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Handles all memory unit lifecycle mutations: promotion, reinforcement, decay,
 * demotion, archiving, supersession, and trust re-evaluation.
 */
@Service
public class MemoryUnitLifecycleService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryUnitLifecycleService.class);

    private final MemoryUnitRepository repository;
    private final ArcMemProperties.UnitConfig config;
    private final ReinforcementPolicy reinforcementPolicy;
    private final DecayPolicy decayPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean lifecycleEventsEnabled;
    private final TrustPipeline trustPipeline;
    private final CanonizationGate canonizationGate;
    private final InvariantEvaluator invariantEvaluator;
    private final BudgetStrategy budgetStrategy;

    private final Map<String, BudgetStrategy> contextBudgetStrategies = new ConcurrentHashMap<>();
    private final List<TrustAuditRecord> trustAuditLog = new CopyOnWriteArrayList<>();

    MemoryUnitLifecycleService(
            MemoryUnitRepository repository,
            ArcMemProperties properties,
            ReinforcementPolicy reinforcementPolicy,
            DecayPolicy decayPolicy,
            ApplicationEventPublisher eventPublisher,
            @Lazy TrustPipeline trustPipeline,
            CanonizationGate canonizationGate,
            InvariantEvaluator invariantEvaluator,
            BudgetStrategy budgetStrategy) {
        this.repository = repository;
        this.config = properties.unit();
        this.reinforcementPolicy = reinforcementPolicy;
        this.decayPolicy = decayPolicy;
        this.eventPublisher = eventPublisher;
        this.lifecycleEventsEnabled = properties.unit().lifecycleEventsEnabled();
        this.trustPipeline = trustPipeline;
        this.canonizationGate = canonizationGate;
        this.invariantEvaluator = invariantEvaluator;
        this.budgetStrategy = budgetStrategy;
    }

    void promote(String propositionId, int initialRank) {
        promote(propositionId, initialRank, Authority.CANON);
    }

    void promote(String propositionId, int initialRank, Authority authorityCeiling) {
        int rank = MemoryUnit.clampRank(initialRank);
        var tier = computeTier(rank);
        var effectiveCeiling = authorityCeiling != null ? authorityCeiling : Authority.CANON;
        repository.promoteToUnit(
                propositionId,
                rank,
                Authority.PROVISIONAL.name(),
                tier.name(),
                effectiveCeiling.name());

        if (effectiveCeiling != Authority.CANON) {
            logger.debug("Promoting proposition {} with authority ceiling {}", propositionId, effectiveCeiling);
        }

        var proposition = repository.findById(propositionId);
        if (proposition != null) {
            var contextId = proposition.getContextIdValue();
            publish(MemoryUnitLifecycleEvent.promoted(this, contextId, propositionId, propositionId, rank));
            var allUnits = findByContext(contextId);
            var protectedIds = new HashSet<String>();
            for (var unit : allUnits) {
                if (unit.pinned()) continue;
                var eval = evaluateInvariants(contextId, ProposedAction.EVICT, allUnits, unit);
                if (eval.hasBlockingViolation()) {
                    protectedIds.add(unit.id());
                    for (var v : eval.violations()) {
                        publish(MemoryUnitLifecycleEvent.invariantViolation(this, contextId, v));
                    }
                    setInvariantSpanAttributes(eval, ProposedAction.EVICT);
                }
            }

            var activeStrategy = resolveStrategy(contextId);
            int effectiveBudget = activeStrategy.computeEffectiveBudget(allUnits, config.budget());
            if (allUnits.size() > effectiveBudget) {
                int excess = allUnits.size() - effectiveBudget;
                var candidates = activeStrategy.selectForEviction(allUnits, excess + protectedIds.size());
                var evictable = candidates.stream()
                        .filter(a -> !protectedIds.contains(a.id()))
                        .limit(excess)
                        .toList();
                for (var victim : evictable) {
                    repository.archiveUnit(victim.id());
                    publish(MemoryUnitLifecycleEvent.evicted(this, contextId, victim.id(), victim.rank()));
                }
            }
        } else {
            logger.warn("Promoted proposition {} not found after write; skipping eviction", propositionId);
        }

        logger.info("Promoted proposition {} to unit with rank {}", propositionId, rank);
    }

    void reinforce(String unitId) {
        reinforce(unitId, true, true);
    }

    void reinforce(String unitId, boolean rankMutationEnabled, boolean authorityPromotionEnabled) {
        repository.reinforceUnit(unitId);

        var nodeOpt = repository.findPropositionNodeById(unitId);
        if (nodeOpt.isEmpty()) {
            logger.warn("MemoryUnit {} not found after reinforcement; skipping rank/authority update", unitId);
            return;
        }
        var node = nodeOpt.get();
        var current = toUnit(node);
        var previousTier = current.memoryTier();
        int newRank = current.rank();
        if (rankMutationEnabled) {
            int boost = reinforcementPolicy.calculateRankBoost(current);
            newRank = MemoryUnit.clampRank(current.rank() + boost);
        }
        var newTier = computeTier(newRank);

        var shouldUpgradeAuthority = authorityPromotionEnabled
                && reinforcementPolicy.shouldUpgradeAuthority(current);

        if (shouldUpgradeAuthority) {
            var allUnits = findByContext(node.getContextId());
            var eval = evaluateInvariants(node.getContextId(), ProposedAction.AUTHORITY_CHANGE, allUnits, current);
            for (var violation : eval.violations()) {
                publish(MemoryUnitLifecycleEvent.invariantViolation(this, node.getContextId(), violation));
            }
            setInvariantSpanAttributes(eval, ProposedAction.AUTHORITY_CHANGE);
            if (eval.hasBlockingViolation()) {
                logger.warn("Authority upgrade of unit {} blocked by MUST invariant(s)", unitId);
                if (rankMutationEnabled) {
                    repository.updateRank(unitId, newRank);
                    updateTierIfChanged(unitId, previousTier, newTier, node.getContextId());
                }
                publish(MemoryUnitLifecycleEvent.reinforced(
                        this, node.getContextId(), unitId, current.rank(), newRank,
                        node.getReinforcementCount()));
                return;
            }
            reEvaluateTrust(unitId, "reinforcement");
            var postTrustNodeOpt = repository.findPropositionNodeById(unitId);
            if (postTrustNodeOpt.isEmpty()) {
                logger.warn("MemoryUnit {} not found after trust re-evaluation; skipping authority upgrade", unitId);
                return;
            }
            var postTrustNode = postTrustNodeOpt.get();
            var postTrustCurrent = toUnit(postTrustNode);
            var ceiling = parseAuthorityCeiling(postTrustNode.getAuthorityCeiling());
            var upgraded = nextAuthority(postTrustCurrent.authority(), ceiling);
            if (upgraded != postTrustCurrent.authority()) {
                repository.setAuthority(unitId, upgraded.name());
                publish(MemoryUnitLifecycleEvent.authorityChanged(
                        this, postTrustNode.getContextId(),
                        unitId, postTrustCurrent.authority(), upgraded,
                        AuthorityChangeDirection.PROMOTED, "reinforcement"));
            }
            if (rankMutationEnabled) {
                repository.updateRank(unitId, newRank);
                updateTierIfChanged(unitId, previousTier, newTier, postTrustNode.getContextId());
            }
            publish(MemoryUnitLifecycleEvent.reinforced(
                    this, postTrustNode.getContextId(),
                    unitId, current.rank(), newRank,
                    postTrustNode.getReinforcementCount()));
            if (upgraded != postTrustCurrent.authority()) {
                logger.info("Reinforced unit {} - rank {} -> {}, authority {} -> {}",
                        unitId, current.rank(), newRank, postTrustCurrent.authority(), upgraded);
            } else {
                logger.debug("Reinforced unit {} - rank {} -> {}, authority remains {} (ceiling={})",
                        unitId, current.rank(), newRank, postTrustCurrent.authority(), ceiling);
            }
        } else {
            if (rankMutationEnabled) {
                repository.updateRank(unitId, newRank);
                updateTierIfChanged(unitId, previousTier, newTier, node.getContextId());
            }
            publish(MemoryUnitLifecycleEvent.reinforced(
                    this, node.getContextId(),
                    unitId, current.rank(), newRank,
                    node.getReinforcementCount()));
            logger.debug("Reinforced unit {} - rank {} -> {} (authorityPromotionEnabled={})",
                    unitId, current.rank(), newRank, authorityPromotionEnabled);
        }
    }

    void demote(String unitId, DemotionReason reason) {
        var nodeOpt = repository.findPropositionNodeById(unitId);
        if (nodeOpt.isEmpty()) {
            logger.warn("Cannot demote unit {} — not found", unitId);
            return;
        }
        var node = nodeOpt.get();
        var currentAuthStr = node.getAuthority();
        var current = currentAuthStr != null
                ? Authority.valueOf(currentAuthStr) : Authority.PROVISIONAL;

        if (current == Authority.CANON && config.canonizationGateEnabled()) {
            canonizationGate.requestDecanonization(
                    unitId, node.getContextId(), node.getText(),
                    reason.name(), "system");
            logger.info("Demotion of CANON unit {} routed through canonization gate (reason={})",
                    unitId, reason);
            return;
        }

        if (current == Authority.PROVISIONAL) {
            archive(unitId, ArchiveReason.CONFLICT_REPLACEMENT);
            logger.info("Demote of PROVISIONAL unit {} resulted in archive (reason={})", unitId, reason);
            return;
        }

        var targetUnit = toUnit(node);
        var allUnits = findByContext(node.getContextId());
        var eval = evaluateInvariants(node.getContextId(), ProposedAction.DEMOTE, allUnits, targetUnit);
        for (var violation : eval.violations()) {
            publish(MemoryUnitLifecycleEvent.invariantViolation(this, node.getContextId(), violation));
        }
        setInvariantSpanAttributes(eval, ProposedAction.DEMOTE);
        if (eval.hasBlockingViolation()) {
            logger.warn("Demotion of unit {} blocked by MUST invariant(s)", unitId);
            return;
        }
        if (eval.hasWarnings()) {
            logger.warn("Demotion of unit {} proceeding despite SHOULD invariant warning(s)", unitId);
        }

        var newAuthority = current.previousLevel();
        repository.setAuthority(unitId, newAuthority.name());
        publish(MemoryUnitLifecycleEvent.authorityChanged(
                this, node.getContextId(),
                unitId, current, newAuthority,
                AuthorityChangeDirection.DEMOTED, reason.name()));
        logger.info("Demoted unit {} from {} to {} (reason={})",
                unitId, current, newAuthority, reason);
    }

    void applyDecay(String unitId, int newRank) {
        var nodeOpt = repository.findPropositionNodeById(unitId);
        if (nodeOpt.isEmpty()) {
            logger.warn("Cannot decay unit {} because it was not found", unitId);
            return;
        }
        var node = nodeOpt.get();
        var currentRank = node.getRank();
        var clampedRank = MemoryUnit.clampRank(newRank);
        if (clampedRank >= currentRank) {
            return;
        }
        var previousTier = computeTier(currentRank);
        var newTier = computeTier(clampedRank);
        repository.updateRank(unitId, clampedRank);
        updateTierIfChanged(unitId, previousTier, newTier, node.getContextId());
        logger.debug("Decayed unit {} rank {} -> {}", unitId, currentRank, clampedRank);

        var unit = toUnit(node);
        if (decayPolicy.shouldDemoteAuthority(unit, clampedRank)) {
            logger.info("Rank decay triggered authority demotion for unit {} (rank={}, authority={})",
                    unitId, clampedRank, unit.authority());
            demote(unitId, DemotionReason.RANK_DECAY);
        }
    }

    void archive(String unitId, ArchiveReason reason) {
        archive(unitId, reason, null);
    }

    void archive(String unitId, ArchiveReason reason, @Nullable String successorId) {
        var nodeOpt = repository.findPropositionNodeById(unitId);
        if (nodeOpt.isEmpty()) {
            logger.warn("Cannot archive unit {} because it was not found", unitId);
            return;
        }
        var node = nodeOpt.get();

        var targetUnit = toUnit(node);
        var allUnits = findByContext(node.getContextId());
        var eval = evaluateInvariants(node.getContextId(), ProposedAction.ARCHIVE, allUnits, targetUnit);
        for (var violation : eval.violations()) {
            publish(MemoryUnitLifecycleEvent.invariantViolation(this, node.getContextId(), violation));
        }
        setInvariantSpanAttributes(eval, ProposedAction.ARCHIVE);
        if (eval.hasBlockingViolation()) {
            logger.warn("Archive of unit {} blocked by MUST invariant(s)", unitId);
            return;
        }
        if (eval.hasWarnings()) {
            logger.warn("Archive of unit {} proceeding despite SHOULD invariant warning(s)", unitId);
        }

        repository.archiveUnit(unitId, successorId);
        publish(MemoryUnitLifecycleEvent.archived(this, node.getContextId(), unitId, reason));
        logger.info("Archived unit {} with reason {}{}", unitId, reason,
                successorId != null ? " (successor=" + successorId + ")" : "");
    }

    void supersede(String predecessorId, String successorId, ArchiveReason reason) {
        var nodeOpt = repository.findPropositionNodeById(predecessorId);
        if (nodeOpt.isEmpty()) {
            logger.warn("Cannot supersede unit {} — not found", predecessorId);
            return;
        }
        var node = nodeOpt.get();
        var predecessorAuthority = node.getAuthority() != null
                ? node.getAuthority() : Authority.PROVISIONAL.name();
        var predecessorRank = node.getRank();

        archive(predecessorId, reason, successorId);
        if (reason == ArchiveReason.REVISION) {
            var predecessorStillActive = repository.findPropositionNodeById(predecessorId)
                    .map(this::isActiveUnitState)
                    .orElse(false);
            if (predecessorStillActive) {
                logger.warn("Revision supersession fallback: forcing archive of predecessor {}", predecessorId);
                repository.archiveUnit(predecessorId, successorId);
                publish(MemoryUnitLifecycleEvent.archived(this, node.getContextId(), predecessorId, reason));
            }
        }

        var supersessionReason = SupersessionReason.fromArchiveReason(reason);
        repository.createSupersessionLink(successorId, predecessorId, supersessionReason);

        publish(MemoryUnitLifecycleEvent.superseded(
                this, node.getContextId(), predecessorId, successorId, supersessionReason));

        var span = Span.current();
        span.setAttribute("supersession.reason", supersessionReason.name());
        span.setAttribute("supersession.predecessor_id", predecessorId);
        span.setAttribute("supersession.successor_id", successorId);
        span.setAttribute("supersession.predecessor_authority", predecessorAuthority);
        span.setAttribute("supersession.predecessor_rank", predecessorRank);
        if (reason == ArchiveReason.REVISION) {
            var authority = authorityOf(node);
            trustAuditLog.add(new TrustAuditRecord(
                    predecessorId,
                    node.getContextId(),
                    authority,
                    authority,
                    Double.NaN,
                    Double.NaN,
                    "revision",
                    Map.of("revision_supersession", 1.0),
                    Instant.now()));
            span.setAttribute("trust.audit.trigger_reason", "revision");
        }

        logger.info("Superseded unit {} by {} (reason={})", predecessorId, successorId, supersessionReason);
    }

    void reEvaluateTrust(String unitId) {
        reEvaluateTrust(unitId, "explicit");
    }

    List<TrustAuditRecord> trustAuditLog(@Nullable String contextId) {
        if (contextId == null) {
            return List.copyOf(trustAuditLog);
        }
        return trustAuditLog.stream()
                .filter(r -> contextId.equals(r.contextId()))
                .toList();
    }

    void clearTrustAuditLog(@Nullable String contextId) {
        if (contextId == null) {
            trustAuditLog.clear();
        } else {
            trustAuditLog.removeIf(r -> contextId.equals(r.contextId()));
        }
    }

    void registerBudgetStrategy(String contextId, BudgetStrategy strategy) {
        contextBudgetStrategies.put(contextId, strategy);
        logger.info("budget.strategy.override contextId={} strategy={}", contextId,
                strategy.getClass().getSimpleName());
    }

    void clearBudgetStrategy(String contextId) {
        contextBudgetStrategies.remove(contextId);
    }

    boolean isActiveUnitState(PropositionNode node) {
        var status = node.getStatus();
        var activeStatus = status == null || status == PropositionStatus.ACTIVE || status == PropositionStatus.PROMOTED;
        return node.getRank() > 0 && activeStatus;
    }

    private void reEvaluateTrust(String unitId, String triggerReason) {
        var nodeOpt = repository.findPropositionNodeById(unitId);
        if (nodeOpt.isEmpty()) {
            logger.warn("Cannot re-evaluate trust for unit {} — not found", unitId);
            return;
        }
        var node = nodeOpt.get();
        var currentAuthStr = node.getAuthority();
        var current = currentAuthStr != null
                ? Authority.valueOf(currentAuthStr) : Authority.PROVISIONAL;

        var trustScore = trustPipeline.evaluate(node, node.getContextId());
        var trustCeiling = trustScore.authorityCeiling();
        var persistedCeiling = parseAuthorityCeiling(node.getAuthorityCeiling());
        var ceiling = minimumAuthority(trustCeiling, persistedCeiling);

        Authority newAuthority;
        if (ceiling.level() < current.level()) {
            logger.info("Trust re-evaluation: unit {} ceiling {} < current authority {} → demoting",
                    unitId, ceiling, current);
            demote(unitId, DemotionReason.TRUST_DEGRADATION);
            newAuthority = repository.findPropositionNodeById(unitId)
                    .map(this::authorityOf)
                    .orElse(current.previousLevel());
        } else {
            logger.debug("Trust re-evaluation: unit {} ceiling {} >= current authority {} — no change",
                    unitId, ceiling, current);
            newAuthority = current;
        }

        trustAuditLog.add(new TrustAuditRecord(
                unitId, node.getContextId(), current, newAuthority,
                Double.NaN, trustScore.score(), triggerReason,
                Map.copyOf(trustScore.signalAudit()), trustScore.evaluatedAt()));
    }

    private BudgetStrategy resolveStrategy(String contextId) {
        return contextBudgetStrategies.getOrDefault(contextId, budgetStrategy);
    }

    private List<MemoryUnit> findByContext(String contextId) {
        return repository.findActiveUnits(contextId).stream()
                .map(this::toUnit)
                .toList();
    }

    private Authority nextAuthority(Authority current, Authority ceiling) {
        var candidate = switch (current) {
            case PROVISIONAL -> Authority.UNRELIABLE;
            case UNRELIABLE -> Authority.RELIABLE;
            case RELIABLE, CANON -> Authority.RELIABLE;
        };
        return candidate.level() <= ceiling.level() ? candidate : current;
    }

    private Authority parseAuthorityCeiling(@Nullable String persistedCeiling) {
        if (persistedCeiling == null || persistedCeiling.isBlank()) {
            return Authority.CANON;
        }
        try {
            return Authority.valueOf(persistedCeiling);
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown authority ceiling '{}' on persisted node; treating as unrestricted",
                    persistedCeiling);
            return Authority.CANON;
        }
    }

    private Authority minimumAuthority(Authority first, Authority second) {
        return first.level() <= second.level() ? first : second;
    }

    private Authority authorityOf(PropositionNode node) {
        var authority = node.getAuthority();
        return authority != null ? Authority.valueOf(authority) : Authority.PROVISIONAL;
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

    private void updateTierIfChanged(String unitId, MemoryTier previousTier,
                                      MemoryTier newTier, String contextId) {
        if (previousTier == newTier) {
            return;
        }
        repository.updateMemoryTier(unitId, newTier.name());
        publish(MemoryUnitLifecycleEvent.tierChanged(this, contextId, unitId, previousTier, newTier));

        var span = Span.current();
        span.setAttribute("unit.id", unitId);
        span.setAttribute("unit.tier", newTier.name());
        span.setAttribute("unit.tier.previous", previousTier.name());

        logger.info("MemoryUnit {} tier changed {} -> {}", unitId, previousTier, newTier);
    }

    private InvariantEvaluation evaluateInvariants(String contextId, ProposedAction action,
            List<MemoryUnit> units, @Nullable MemoryUnit target) {
        var eval = invariantEvaluator.evaluate(contextId, action, units, target);
        return eval != null ? eval : new InvariantEvaluation(List.of(), 0);
    }

    private void setInvariantSpanAttributes(InvariantEvaluation eval, ProposedAction action) {
        var span = Span.current();
        span.setAttribute("invariant.checked_count", (long) eval.checkedCount());
        span.setAttribute("invariant.violated_count", (long) eval.violations().size());
        span.setAttribute("invariant.must_violations",
                eval.violations().stream()
                        .filter(v -> v.strength() == InvariantStrength.MUST).count());
        span.setAttribute("invariant.should_violations",
                eval.violations().stream()
                        .filter(v -> v.strength() == InvariantStrength.SHOULD).count());
        if (eval.hasBlockingViolation()) {
            span.setAttribute("invariant.blocked_action", action.name());
        }
    }

    MemoryUnit toUnit(PropositionNode node) {
        var authority = Authority.valueOf(
                node.getAuthority() != null ? node.getAuthority() : Authority.PROVISIONAL.name());
        var tier = computeTier(node.getRank());
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
                tier
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
