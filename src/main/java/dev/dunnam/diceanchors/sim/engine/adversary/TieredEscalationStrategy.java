package dev.dunnam.diceanchors.sim.engine.adversary;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.sim.engine.AttackStrategy;
import dev.dunnam.diceanchors.sim.engine.DriftStrategyDefinition;
import dev.dunnam.diceanchors.sim.engine.SimulationScenario;
import dev.dunnam.diceanchors.sim.engine.StrategyCatalog;
import dev.dunnam.diceanchors.sim.engine.StrategyTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default {@link AdversaryStrategy} implementation.
 * <p>
 * Targets the weakest (lowest-rank) active anchors, skipping those already in
 * conflict. Escalates strategy tier on a per-target basis — when the last attack
 * against a specific target failed, the next tier is tried for that target.
 * Chains multi-turn SETUP → BUILD → PAYOFF sequences for multi-turn strategies.
 * All decisions are derived from {@link AttackHistory}; no mutable instance state is kept.
 * <p>
 * Invariants:
 * <ul>
 *   <li>I4: tier never exceeds {@code AdversaryConfig.maxEscalationTier} for each target</li>
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

    @Override
    public AttackPlan selectAttack(List<Anchor> active, List<Anchor> conflicted, AttackHistory history) {
        var activeSequence = detectActiveSequence(history);
        if (activeSequence == null) {
            return planNewAttack(active, conflicted, history);
        } else {
            return continueSequence(activeSequence, active, conflicted);
        }
    }

    // -------------------------------------------------------------------------
    // Sequence detection
    // -------------------------------------------------------------------------

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
            return null; // standalone attack — no active sequence
        }
        if (seq.phase().equals(AttackSequence.PAYOFF)) {
            return null; // sequence complete
        }
        return lastOutcome; // in SETUP or BUILD — continue it
    }

    // -------------------------------------------------------------------------
    // Sequence continuation
    // -------------------------------------------------------------------------

    /**
     * Advance an in-progress sequence: SETUP → BUILD at tier+1, BUILD → PAYOFF at maxEscalationTier.
     */
    private AttackPlan continueSequence(AttackOutcome lastOutcome, List<Anchor> active, List<Anchor> conflicted) {
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

        var strategies = selectStrategies(nextTier);
        var rationale = "sequence=%s phase=%s tier=%s targets=%d"
                .formatted(sequenceId, nextPhase, nextTier, targets.size());
        logger.debug("Continuing sequence: id={}, phase={}, tier={}", sequenceId, nextPhase, nextTier);

        return new AttackPlan(targets, strategies, nextTier, rationale, new AttackSequence(sequenceId, nextPhase));
    }

    // -------------------------------------------------------------------------
    // New attack planning
    // -------------------------------------------------------------------------

    /**
     * Full planning path when no active sequence exists.
     */
    private AttackPlan planNewAttack(List<Anchor> active, List<Anchor> conflicted, AttackHistory history) {
        var targets = selectTargets(active, conflicted, history);
        var tier = determineTier(targets, history);
        var strategies = selectStrategies(tier);

        // Check whether the first strategy is a multi-turn strategy
        var firstStrategy = strategies.get(0);
        var isMultiTurn = catalog.findById(firstStrategy.name())
                .map(DriftStrategyDefinition::multiTurn)
                .orElse(false);

        if (isMultiTurn) {
            var sequenceId = UUID.randomUUID().toString().substring(0, 8);
            var setupLevel = Math.max(1, tier.level() - 1);
            var setupTier = tierFromLevel(setupLevel);
            var setupStrategies = selectStrategies(setupTier);
            var rationale = "sequence=%s phase=SETUP tier=%s targets=%d historySize=%d"
                    .formatted(sequenceId, setupTier, targets.size(), history.size());
            logger.debug("Starting multi-turn sequence: id={}, setupTier={}, tier={}", sequenceId, setupTier, tier);
            return new AttackPlan(targets, setupStrategies, setupTier, rationale, new AttackSequence(sequenceId, AttackSequence.SETUP));
        }

        var rationale = "standalone tier=%s targets=%d historySize=%d"
                .formatted(tier, targets.size(), history.size());
        logger.debug("Standalone attack: tier={}, targets={}", tier, targets);
        return new AttackPlan(targets, strategies, tier, rationale, null);
    }

    // -------------------------------------------------------------------------
    // Target selection
    // -------------------------------------------------------------------------

    /**
     * Select attack targets from active anchors.
     * Excludes conflicted anchors and applies target switching for consistently-resisting targets.
     * Falls back to full active list if filtering leaves nothing.
     */
    private List<String> selectTargets(List<Anchor> active, List<Anchor> conflicted, AttackHistory history) {
        var conflictedIds = conflicted.stream().map(Anchor::id).collect(Collectors.toUnmodifiableSet());

        var eligible = active.stream()
                .filter(a -> !conflictedIds.contains(a.id()))
                .sorted(Comparator.comparingInt(Anchor::rank))
                .toList();

        // Apply target switching — exclude anchors that have consistently resisted
        var switched = applyTargetSwitching(eligible, history);

        // If switching filtered everything out, fall back to all active sorted by rank
        if (switched.isEmpty()) {
            switched = active.stream()
                    .sorted(Comparator.comparingInt(Anchor::rank))
                    .toList();
        }

        int count = (int) Math.max(1, Math.ceil(config.aggressiveness() * switched.size()));
        return switched.stream()
                .limit(count)
                .map(Anchor::text)
                .toList();
    }

    /**
     * Filters out anchors that have resisted the last {@link #TARGET_SWITCH_THRESHOLD} attacks targeting them.
     * If filtering would leave an empty list, the original list is returned unchanged.
     */
    private List<Anchor> applyTargetSwitching(List<Anchor> eligible, AttackHistory history) {
        var all = history.lastN(Integer.MAX_VALUE);

        var filtered = eligible.stream()
                .filter(anchor -> {
                    var lastAttacksOnTarget = all.stream()
                            .filter(o -> o.plan().targetFacts().contains(anchor.text()))
                            .toList();
                    if (lastAttacksOnTarget.size() < TARGET_SWITCH_THRESHOLD) {
                        return true; // not enough history to switch away — keep it
                    }
                    var lastTwo = lastAttacksOnTarget.subList(
                            lastAttacksOnTarget.size() - TARGET_SWITCH_THRESHOLD,
                            lastAttacksOnTarget.size()
                    );
                    // Exclude only if all of the last TARGET_SWITCH_THRESHOLD outcomes were NONE
                    return lastTwo.stream().anyMatch(AttackOutcome::succeeded);
                })
                .toList();

        return filtered.isEmpty() ? eligible : filtered;
    }

    // -------------------------------------------------------------------------
    // Tier determination
    // -------------------------------------------------------------------------

    /**
     * Determine the escalation tier for this attack.
     * Evaluates each target independently: if the last attack on that target failed, escalate by 1.
     * Takes the maximum tier across all targets, capped at {@code maxEscalationTier}.
     */
    private StrategyTier determineTier(List<String> targets, AttackHistory history) {
        var all = history.lastN(Integer.MAX_VALUE);
        int maxLevel = 1;

        for (var target : targets) {
            // Find the most recent outcome that targeted this anchor
            var lastForTarget = all.stream()
                    .filter(o -> o.plan().targetFacts().contains(target))
                    .reduce((first, second) -> second) // last element
                    .orElse(null);

            int targetLevel;
            if (lastForTarget != null && "NONE".equals(lastForTarget.verdictSeverity())) {
                // Last attack on this target failed — escalate
                var escalated = lastForTarget.plan().tier().level() + 1;
                targetLevel = Math.min(escalated, config.maxEscalationTier());
            } else {
                targetLevel = 1; // no history or last attack succeeded — start at BASIC
            }
            if (targetLevel > maxLevel) {
                maxLevel = targetLevel;
            }
        }

        var tier = tierFromLevel(maxLevel);
        logger.debug("Determined tier {} (level={}) for targets={}", tier, maxLevel, targets);
        return tier;
    }

    // -------------------------------------------------------------------------
    // Strategy selection
    // -------------------------------------------------------------------------

    /**
     * Select strategies at the given tier.
     * Tries preferred strategies at tier first, then first available at tier,
     * then any at or below tier, then falls back to {@link AttackStrategy#SUBTLE_REFRAME}.
     */
    private List<AttackStrategy> selectStrategies(StrategyTier tier) {
        var preferred = config.preferredStrategies();
        if (!preferred.isEmpty()) {
            var catalogAtTier = catalog.findByTier(tier);
            var preferredIds = Set.copyOf(preferred);
            var matches = catalogAtTier.stream()
                    .filter(d -> preferredIds.contains(d.id()))
                    .map(d -> AttackStrategy.fromString(d.id()))
                    .filter(s -> s != null)
                    .toList();
            if (!matches.isEmpty()) {
                return List.of(matches.get(0));
            }
        }

        // Fall back to first available strategy at requested tier
        var atTier = catalog.findByTier(tier);
        for (var def : atTier) {
            var strategy = AttackStrategy.fromString(def.id());
            if (strategy != null) {
                return List.of(strategy);
            }
        }

        // Last resort: any strategy at or below the requested tier
        var fallback = catalog.findByTierAtOrBelow(tier);
        for (var def : fallback) {
            var strategy = AttackStrategy.fromString(def.id());
            if (strategy != null) {
                return List.of(strategy);
            }
        }

        // Safety net
        return List.of(AttackStrategy.SUBTLE_REFRAME);
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private static StrategyTier tierFromLevel(int level) {
        for (var t : StrategyTier.values()) {
            if (t.level() == level) {
                return t;
            }
        }
        return StrategyTier.EXPERT;
    }
}
