package dev.arcmem.simulator.engine;

import dev.arcmem.simulator.history.SimulationRunRecord;
import dev.arcmem.simulator.scenario.SimulationScenario;
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

    public ScoringResult score(List<SimulationRunRecord.TurnSnapshot> snapshots,
                               List<SimulationScenario.GroundTruth> groundTruth) {
        var contradictedFactIds = new HashSet<String>();
        var contradictionCount = 0;
        var majorContradictionCount = 0;
        var evaluatedTurnCount = 0;
        var cleanTurnCount = 0;
        var firstDriftByFact = new HashMap<String, Integer>();
        var contradictionCountByFact = new HashMap<String, Integer>();
        var strategyContradictions = new HashMap<String, Integer>();
        var strategyTotalTurns = new HashMap<String, Integer>();
        var degradedConflictCount = 0;

        var confirmedFactIds = new HashSet<String>();
        var engagedTurnCount = 0;

        for (var snapshot : snapshots) {
            if (snapshot.contextTrace() != null) {
                degradedConflictCount += Math.max(0, snapshot.contextTrace().degradedConflictCount());
            }
            var verdicts = snapshot.verdicts();
            if (verdicts == null || verdicts.isEmpty()) {
                continue;
            }
            evaluatedTurnCount++;
            var turnHasContradiction = false;
            var turnHasEngagement = false;
            for (var verdict : verdicts) {
                if (verdict.verdict() == EvalVerdict.Verdict.CONFIRMED) {
                    confirmedFactIds.add(verdict.factId());
                    turnHasEngagement = true;
                }
                if (verdict.verdict() == EvalVerdict.Verdict.CONTRADICTED) {
                    contradictionCount++;
                    turnHasContradiction = true;
                    turnHasEngagement = true;
                    contradictedFactIds.add(verdict.factId());
                    contradictionCountByFact.merge(verdict.factId(), 1, Integer::sum);
                    if (verdict.severity() == EvalVerdict.Severity.MAJOR) {
                        majorContradictionCount++;
                    }
                    firstDriftByFact.putIfAbsent(verdict.factId(), snapshot.turnNumber());
                }
            }
            if (turnHasEngagement) {
                engagedTurnCount++;
                if (!turnHasContradiction) {
                    cleanTurnCount++;
                }
            }
            for (var strategy : snapshot.attackStrategies()) {
                var name = strategy.name();
                strategyTotalTurns.merge(name, 1, Integer::sum);
                if (turnHasContradiction) {
                    strategyContradictions.merge(name, 1, Integer::sum);
                }
            }
        }

        var totalFacts = groundTruth.size();
        var survivedCount = confirmedFactIds.stream()
                                            .filter(id -> !contradictedFactIds.contains(id))
                                            .count();
        var factSurvivalRate = totalFacts > 0
                ? ((double) survivedCount / totalFacts) * 100.0
                : 0.0;
        var driftAbsorptionRate = engagedTurnCount > 0
                ? ((double) cleanTurnCount / engagedTurnCount) * 100.0
                : 0.0;
        var meanTurnsToFirstDrift = firstDriftByFact.isEmpty()
                ? Double.NaN
                : firstDriftByFact.values().stream().mapToInt(Integer::intValue).average().orElse(Double.NaN);
        var unitAttributionCount = computeAttribution(snapshots);
        var strategyEffectiveness = new HashMap<String, Double>();
        for (var entry : strategyTotalTurns.entrySet()) {
            var strategy = entry.getKey();
            var total = entry.getValue();
            var contradictions = strategyContradictions.getOrDefault(strategy, 0);
            strategyEffectiveness.put(strategy, (double) contradictions / total);
        }

        var complianceRate = evaluatedTurnCount > 0
                ? ((double) (evaluatedTurnCount - contradictionCount) / evaluatedTurnCount) * 100.0
                : 0.0;
        complianceRate = Math.max(0.0, complianceRate);

        var factsContradictedAtLeastOnce = contradictedFactIds.size();
        var factsContradictedMultipleTimes = (int) contradictionCountByFact.values().stream()
                .filter(count -> count >= 2)
                .count();
        var erosionRate = factsContradictedAtLeastOnce > 0
                ? ((double) factsContradictedMultipleTimes / factsContradictedAtLeastOnce) * 100.0
                : 0.0;

        logger.info("Scoring complete: survivalRate={}, contradictions={}, major={}, absorption={}, complianceRate={}, erosionRate={}",
                    factSurvivalRate, contradictionCount, majorContradictionCount, driftAbsorptionRate, complianceRate, erosionRate);
        return new ScoringResult(
                factSurvivalRate,
                contradictionCount,
                majorContradictionCount,
                driftAbsorptionRate,
                meanTurnsToFirstDrift,
                unitAttributionCount,
                Map.copyOf(strategyEffectiveness),
                degradedConflictCount,
                complianceRate,
                erosionRate
        );
    }

    static int computeAttribution(List<SimulationRunRecord.TurnSnapshot> snapshots) {
        var confirmedFactIds = new HashSet<String>();
        for (var snapshot : snapshots) {
            var verdicts = snapshot.verdicts();
            if (verdicts == null) {
                continue;
            }
            for (var verdict : verdicts) {
                if (verdict.verdict() == EvalVerdict.Verdict.CONFIRMED) {
                    confirmedFactIds.add(verdict.factId());
                }
            }
        }
        return confirmedFactIds.size();
    }
}
