package dev.dunnam.diceanchors.sim.engine;

import dev.dunnam.diceanchors.anchor.Anchor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Computes aggregate scoring metrics from simulation turn snapshots
 * and ground truth definitions.
 */
@Service
public class ScoringService {

    private static final Logger logger = LoggerFactory.getLogger(ScoringService.class);

    /**
     * Score a simulation run against its ground truth facts.
     *
     * @param snapshots       turn-by-turn snapshots from the run
     * @param groundTruth     the scenario's ground truth definitions
     * @param injectedAnchors anchors that were active during the run
     *
     * @return aggregate scoring metrics
     */
    public ScoringResult score(List<SimulationRunRecord.TurnSnapshot> snapshots,
                               List<SimulationScenario.GroundTruth> groundTruth,
                               List<Anchor> injectedAnchors) {
        var contradictedFactIds = new HashSet<String>();
        var contradictionCount = 0;
        var majorContradictionCount = 0;
        var evaluatedTurnCount = 0;
        var cleanTurnCount = 0;
        var firstDriftByFact = new HashMap<String, Integer>();
        var strategyContradictions = new HashMap<String, Integer>();
        var strategyTotalTurns = new HashMap<String, Integer>();

        for (var snapshot : snapshots) {
            var verdicts = snapshot.verdicts();
            if (verdicts == null || verdicts.isEmpty()) {
                continue;
            }
            evaluatedTurnCount++;
            var turnHasContradiction = false;
            for (var verdict : verdicts) {
                if (verdict.verdict() == EvalVerdict.Verdict.CONTRADICTED) {
                    contradictionCount++;
                    turnHasContradiction = true;
                    contradictedFactIds.add(verdict.factId());
                    if (verdict.severity() == EvalVerdict.Severity.MAJOR) {
                        majorContradictionCount++;
                    }
                    firstDriftByFact.putIfAbsent(verdict.factId(), snapshot.turnNumber());
                }
            }
            if (!turnHasContradiction) {
                cleanTurnCount++;
            }
            if (snapshot.attackStrategy() != null) {
                var strategy = snapshot.attackStrategy().name();
                strategyTotalTurns.merge(strategy, 1, Integer::sum);
                if (turnHasContradiction) {
                    strategyContradictions.merge(strategy, 1, Integer::sum);
                }
            }
        }

        var totalFacts = groundTruth.size();
        var factSurvivalRate = totalFacts > 0
                ? ((double) (totalFacts - contradictedFactIds.size()) / totalFacts) * 100.0
                : 100.0;
        var driftAbsorptionRate = evaluatedTurnCount > 0
                ? ((double) cleanTurnCount / evaluatedTurnCount) * 100.0
                : 100.0;
        var meanTurnsToFirstDrift = firstDriftByFact.isEmpty()
                ? Double.NaN
                : firstDriftByFact.values().stream().mapToInt(Integer::intValue).average().orElse(Double.NaN);
        var anchorAttributionCount = computeAttribution(injectedAnchors, groundTruth);
        var strategyEffectiveness = new HashMap<String, Double>();
        for (var entry : strategyTotalTurns.entrySet()) {
            var strategy = entry.getKey();
            var total = entry.getValue();
            var contradictions = strategyContradictions.getOrDefault(strategy, 0);
            strategyEffectiveness.put(strategy, (double) contradictions / total);
        }

        logger.info("Scoring complete: survivalRate={}, contradictions={}, major={}, absorption={}",
                    factSurvivalRate, contradictionCount, majorContradictionCount, driftAbsorptionRate);
        return new ScoringResult(
                factSurvivalRate,
                contradictionCount,
                majorContradictionCount,
                driftAbsorptionRate,
                meanTurnsToFirstDrift,
                anchorAttributionCount,
                Map.copyOf(strategyEffectiveness)
        );
    }

    /**
     * Count ground truth facts that have at least one matching injected anchor.
     * Matching uses bidirectional normalized substring containment.
     */
    static int computeAttribution(List<Anchor> injectedAnchors, List<SimulationScenario.GroundTruth> groundTruth) {
        var count = 0;
        for (var fact : groundTruth) {
            var normalizedFact = normalize(fact.text());
            for (var anchor : injectedAnchors) {
                var normalizedAnchor = normalize(anchor.text());
                if (normalizedFact.contains(normalizedAnchor) || normalizedAnchor.contains(normalizedFact)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private static String normalize(String text) {
        return text.toLowerCase().replaceAll("[^a-z0-9 ]", "");
    }
}
