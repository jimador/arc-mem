package dev.arcmem.simulator.adversary;
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

import dev.arcmem.simulator.engine.AttackStrategy;
import dev.arcmem.simulator.scenario.DriftStrategyDefinition;
import dev.arcmem.simulator.scenario.SimulationScenario;
import dev.arcmem.simulator.engine.StrategyCatalog;
import dev.arcmem.simulator.engine.StrategyTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default {@link AdversaryStrategy} implementation.
 * <p>
 * Targets the weakest (lowest-rank) active units, skipping those already in
 * conflict. Determines a preferred strategy tier based on how many attacks have
 * occurred so far (not failure-based escalation), then returns a hint plan with
 * all strategies at that tier. The actual strategy selection is delegated to the
 * LLM in {@link AdaptiveAttackPrompter}, which may choose any combination.
 * Chains multi-turn SETUP → BUILD → PAYOFF sequences for multi-turn strategies.
 * All decisions are derived from {@link AttackHistory}; no mutable instance state is kept.
 * <p>
 * Invariants:
 * <ul>
 *   <li>I4: preferred tier never exceeds {@code AdversaryConfig.maxEscalationTier}</li>
 * </ul>
 */
public class TieredEscalationStrategy implements AdversaryStrategy {

    private static final Logger logger = LoggerFactory.getLogger(TieredEscalationStrategy.class);
    private static final int TARGET_SWITCH_THRESHOLD = 2;

    private final SimulationScenario.AdversaryConfig config;
    private final StrategyCatalog catalog;

    public TieredEscalationStrategy(SimulationScenario.AdversaryConfig config, StrategyCatalog catalog) {
        this.config = config;
        this.catalog = catalog;
    }

    /**
     * Produce an attack plan hint for this turn.
     * <p>
     * The returned plan contains the preferred strategies at the tier appropriate for how many
     * attacks have occurred. The LLM in {@link AdaptiveAttackPrompter} will see all available
     * strategies and may deviate freely from these suggestions.
     */
    @Override
    public AttackPlan selectAttack(List<MemoryUnit> active, List<MemoryUnit> conflicted, AttackHistory history) {
        var activeSequence = detectActiveSequence(history);
        if (activeSequence == null) {
            return planNewAttack(active, conflicted, history);
        } else {
            return continueSequence(activeSequence, history);
        }
    }

    /**
     * Returns the last outcome if we are mid-sequence (SETUP or BUILD), else null.
     * Returns null when: history is empty, last attack was standalone, or last phase was PAYOFF.
     */
    private AttackOutcome detectActiveSequence(AttackHistory history) {
        var recent = history.lastN(1);
        if (recent.isEmpty()) {
            return null;
        }
        var lastOutcome = recent.get(0);
        var seq = lastOutcome.plan().sequence();
        if (seq == null) {
            return null;
        }
        if (seq.phase().equals(AttackSequence.PAYOFF)) {
            return null;
        }
        return lastOutcome;
    }

    /**
     * Advance an in-progress sequence: SETUP → BUILD at tier+1, BUILD → PAYOFF at maxEscalationTier.
     */
    private AttackPlan continueSequence(AttackOutcome lastOutcome, AttackHistory history) {
        var seq = lastOutcome.plan().sequence();
        var sequenceId = seq.id();
        var targets = lastOutcome.plan().targetFacts();

        String nextPhase;
        StrategyTier nextTier;

        if (seq.phase().equals(AttackSequence.SETUP)) {
            nextPhase = AttackSequence.BUILD;
            var setupLevel = lastOutcome.plan().tier().level();
            var nextLevel = Math.min(setupLevel + 1, config.maxEscalationTier());
            nextTier = tierFromLevel(nextLevel);
        } else {
            // BUILD → PAYOFF
            nextPhase = AttackSequence.PAYOFF;
            nextTier = tierFromLevel(config.maxEscalationTier());
        }

        var strategies = preferredStrategies(nextTier);
        var rationale = "sequence=%s phase=%s tier=%s targets=%d"
                .formatted(sequenceId, nextPhase, nextTier, targets.size());
        logger.debug("Continuing sequence: id={}, phase={}, tier={}", sequenceId, nextPhase, nextTier);

        return new AttackPlan(targets, strategies, nextTier, rationale, new AttackSequence(sequenceId, nextPhase));
    }

    private AttackPlan planNewAttack(List<MemoryUnit> active, List<MemoryUnit> conflicted, AttackHistory history) {
        var targets = selectTargets(active, conflicted, history);
        var tier = determineTier(history);
        var strategies = preferredStrategies(tier);

        var firstStrategy = strategies.get(0);
        var isMultiTurn = catalog.findById(firstStrategy.name())
                                 .map(DriftStrategyDefinition::multiTurn)
                                 .orElse(false);

        if (isMultiTurn) {
            var sequenceId = UUID.randomUUID().toString().substring(0, 8);
            var setupLevel = Math.max(1, tier.level() - 1);
            var setupTier = tierFromLevel(setupLevel);
            var setupStrategies = preferredStrategies(setupTier);
            var rationale = "sequence=%s phase=SETUP tier=%s targets=%d attackCount=%d"
                    .formatted(sequenceId, setupTier, targets.size(), history.size());
            logger.debug("Starting multi-turn sequence: id={}, setupTier={}, tier={}", sequenceId, setupTier, tier);
            return new AttackPlan(targets, setupStrategies, setupTier, rationale, new AttackSequence(sequenceId, AttackSequence.SETUP));
        }

        var rationale = "standalone tier=%s targets=%d attackCount=%d"
                .formatted(tier, targets.size(), history.size());
        logger.debug("Standalone attack hint: tier={}, targets={}", tier, targets);
        return new AttackPlan(targets, strategies, tier, rationale, null);
    }

    /**
     * Select attack targets from active units.
     * Excludes conflicted units and applies target switching for consistently-resisting targets.
     * Falls back to full active list if filtering leaves nothing.
     */
    private List<String> selectTargets(List<MemoryUnit> active, List<MemoryUnit> conflicted, AttackHistory history) {
        var conflictedIds = conflicted.stream().map(MemoryUnit::id).collect(Collectors.toUnmodifiableSet());

        var eligible = active.stream()
                             .filter(a -> !conflictedIds.contains(a.id()))
                             .sorted(Comparator.comparingInt(MemoryUnit::rank))
                             .toList();

        var switched = applyTargetSwitching(eligible, history);

        if (switched.isEmpty()) {
            switched = active.stream()
                             .sorted(Comparator.comparingInt(MemoryUnit::rank))
                             .toList();
        }

        int count = (int) Math.max(1, Math.ceil(config.aggressiveness() * switched.size()));
        return switched.stream()
                       .limit(count)
                       .map(MemoryUnit::text)
                       .toList();
    }

    /**
     * Filters out units that have resisted the last {@link #TARGET_SWITCH_THRESHOLD} attacks targeting them.
     * If filtering would leave an empty list, the original list is returned unchanged.
     */
    private List<MemoryUnit> applyTargetSwitching(List<MemoryUnit> eligible, AttackHistory history) {
        var all = history.lastN(Integer.MAX_VALUE);

        var filtered = eligible.stream()
                               .filter(unit -> {
                                   var lastAttacksOnTarget = all.stream()
                                                                .filter(o -> o.plan().targetFacts().contains(unit.text()))
                                                                .toList();
                                   if (lastAttacksOnTarget.size() < TARGET_SWITCH_THRESHOLD) {
                                       return true;
                                   }
                                   var lastTwo = lastAttacksOnTarget.subList(
                                           lastAttacksOnTarget.size() - TARGET_SWITCH_THRESHOLD,
                                           lastAttacksOnTarget.size()
                                   );
                                   return lastTwo.stream().anyMatch(AttackOutcome::succeeded);
                               })
                               .toList();

        return filtered.isEmpty() ? eligible : filtered;
    }

    /**
     * Determine the preferred strategy tier for this attack based on how many attacks
     * have already occurred in this run. More attacks → higher tier preference, capped
     * at {@code maxEscalationTier}.
     *
     * <pre>
     *  0–2 attacks → BASIC
     *  3–5 attacks → INTERMEDIATE
     *  6–9 attacks → ADVANCED
     * 10+  attacks → EXPERT
     * </pre>
     */
    private StrategyTier determineTier(AttackHistory history) {
        var attackCount = history.size();
        int level;
        if (attackCount < 3) {
            level = 1;
        } else if (attackCount < 6) {
            level = 2;
        } else if (attackCount < 10) {
            level = 3;
        } else {
            level = 4;
        }
        var capped = Math.min(level, config.maxEscalationTier());
        var tier = tierFromLevel(capped);
        logger.debug("Preferred tier {} (attackCount={})", tier, attackCount);
        return tier;
    }

    /**
     * Return all catalog strategies at the given tier as the preference hint for the LLM.
     * The LLM may choose any of these, combine them, or deviate to other tiers entirely.
     * Falls back to any strategy at or below tier, then to {@link AttackStrategy#SUBTLE_REFRAME}.
     */
    private List<AttackStrategy> preferredStrategies(StrategyTier tier) {
        var preferred = config.preferredStrategies();

        if (!preferred.isEmpty()) {
            var preferredSet = java.util.Set.copyOf(preferred);
            var matches = catalog.findByTier(tier).stream()
                                 .filter(d -> preferredSet.contains(d.id()))
                                 .map(d -> AttackStrategy.fromString(d.id()))
                                 .filter(s -> s != null)
                                 .toList();
            if (!matches.isEmpty()) {
                return matches;
            }
        }

        var atTier = catalog.findByTier(tier).stream()
                            .map(d -> AttackStrategy.fromString(d.id()))
                            .filter(s -> s != null)
                            .toList();
        if (!atTier.isEmpty()) {
            return atTier;
        }

        var fallback = catalog.findByTierAtOrBelow(tier).stream()
                              .map(d -> AttackStrategy.fromString(d.id()))
                              .filter(s -> s != null)
                              .toList();
        return fallback.isEmpty() ? List.of(AttackStrategy.SUBTLE_REFRAME) : fallback;
    }

    private static StrategyTier tierFromLevel(int level) {
        for (var t : StrategyTier.values()) {
            if (t.level() == level) {
                return t;
            }
        }
        return StrategyTier.EXPERT;
    }
}
