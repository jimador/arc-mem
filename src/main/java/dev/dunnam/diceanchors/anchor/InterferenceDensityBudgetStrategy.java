package dev.dunnam.diceanchors.anchor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Budget strategy that reduces the effective anchor budget when semantic overlap
 * (measured by conflict graph edge density) exceeds a configurable threshold.
 * <p>
 * Motivation: Guo et al. (2025) "Sleeping LLMs" found a sharp phase transition at
 * 13-14 facts for an 8B weight-edited model -- recall drops from 0.92 to 0.57 when
 * representational capacity is exceeded. While dice-anchors uses prompt injection
 * rather than weight editing, the interference principle applies: semantically overlapping
 * anchors compete for the LLM's attention budget more aggressively than diverse anchors.
 * <p>
 * Budget reduction formula (linear): when density exceeds the reduction threshold,
 * {@code effectiveBudget = max(1, floor(rawBudget * (1.0 - density * reductionFactor)))}.
 * <p>
 * Eviction is cluster-aware: candidates are drawn from the largest cluster first
 * (highest interference), targeting the densest competition zone. Falls back to
 * global lowest-rank eviction when no clusters exist.
 */
public final class InterferenceDensityBudgetStrategy implements BudgetStrategy {

    private static final Logger logger = LoggerFactory.getLogger(InterferenceDensityBudgetStrategy.class);

    private final InterferenceDensityCalculator calculator;
    private final ConflictIndex conflictIndex;
    private final double warningThreshold;
    private final double reductionThreshold;
    private final double reductionFactor;

    public InterferenceDensityBudgetStrategy(
            InterferenceDensityCalculator calculator,
            ConflictIndex conflictIndex,
            double warningThreshold,
            double reductionThreshold,
            double reductionFactor) {
        this.calculator = calculator;
        this.conflictIndex = conflictIndex;
        this.warningThreshold = warningThreshold;
        this.reductionThreshold = reductionThreshold;
        this.reductionFactor = reductionFactor;
    }

    @Override
    public int computeEffectiveBudget(List<Anchor> activeAnchors, int rawBudget) {
        double density = calculator.computeDensity(activeAnchors, conflictIndex);
        int headroom = rawBudget - activeAnchors.size();

        logger.info("budget.density.score={} budget.raw={} budget.headroom={}",
                density, rawBudget, headroom);

        if (density >= warningThreshold && density < reductionThreshold) {
            logger.warn("Interference density {} exceeds warning threshold {} -- monitor for phase transition",
                    density, warningThreshold);
        }

        if (density < reductionThreshold) {
            logger.info("budget.effective={} (no reduction, density below threshold {})",
                    rawBudget, reductionThreshold);
            return rawBudget;
        }

        // Phase transition risk: Guo et al. (2025) phase transition applies
        logger.warn("Interference density {} exceeds reduction threshold {} -- phase transition risk, reducing budget",
                density, reductionThreshold);

        int effectiveBudget = Math.max(1, (int) Math.floor(rawBudget * (1.0 - density * reductionFactor)));
        effectiveBudget = Math.min(rawBudget, effectiveBudget);

        var clusters = calculator.identifyClusters(activeAnchors, conflictIndex);
        logger.info("budget.effective={} budget.cluster.count={} budget.cluster.maxSize={}",
                effectiveBudget, clusters.size(),
                clusters.stream().mapToInt(AnchorCluster::size).max().orElse(0));

        return effectiveBudget;
    }

    @Override
    public List<Anchor> selectForEviction(List<Anchor> activeAnchors, int excess) {
        var clusters = calculator.identifyClusters(activeAnchors, conflictIndex);
        if (clusters.isEmpty()) {
            logger.info("budget.eviction.reason=global_fallback (no clusters)");
            return countBasedFallback(activeAnchors, excess);
        }
        return clusterAwareEviction(activeAnchors, clusters, excess);
    }

    private List<Anchor> clusterAwareEviction(
            List<Anchor> activeAnchors, List<AnchorCluster> clusters, int excess) {
        // Sort clusters by size descending (largest = most interference first)
        var sortedClusters = clusters.stream()
                .sorted(Comparator.comparingInt(AnchorCluster::size).reversed())
                .toList();

        var anchorById = new HashMap<String, Anchor>();
        for (var a : activeAnchors) {
            anchorById.put(a.id(), a);
        }

        var evictionList = new ArrayList<Anchor>();
        var alreadySelected = new HashSet<String>();

        for (var cluster : sortedClusters) {
            if (evictionList.size() >= excess) {
                break;
            }
            var candidate = cluster.anchorIds().stream()
                    .map(anchorById::get)
                    .filter(a -> a != null && !a.pinned() && a.authority() != Authority.CANON
                                 && !alreadySelected.contains(a.id()))
                    .min(Comparator.comparingInt(Anchor::rank));

            if (candidate.isPresent()) {
                var victim = candidate.get();
                evictionList.add(victim);
                alreadySelected.add(victim.id());
                logger.info("budget.eviction.reason=cluster_eviction anchor={} rank={} cluster_size={}",
                        victim.id(), victim.rank(), cluster.size());
            }
        }

        // Fill remaining slots with global fallback when cluster eviction is insufficient
        if (evictionList.size() < excess) {
            var remaining = countBasedFallback(activeAnchors, excess + alreadySelected.size());
            for (var a : remaining) {
                if (!alreadySelected.contains(a.id()) && evictionList.size() < excess) {
                    evictionList.add(a);
                    logger.info("budget.eviction.reason=global_fallback anchor={} rank={}", a.id(), a.rank());
                }
            }
        }

        return evictionList;
    }

    private static List<Anchor> countBasedFallback(List<Anchor> activeAnchors, int excess) {
        return activeAnchors.stream()
                .filter(a -> !a.pinned() && a.authority() != Authority.CANON)
                .sorted(Comparator.comparingInt(Anchor::rank))
                .limit(excess)
                .toList();
    }
}
