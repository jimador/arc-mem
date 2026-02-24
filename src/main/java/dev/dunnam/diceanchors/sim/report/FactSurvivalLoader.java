package dev.dunnam.diceanchors.sim.report;

import dev.dunnam.diceanchors.sim.benchmark.ExperimentReport;
import dev.dunnam.diceanchors.sim.engine.EvalVerdict;
import dev.dunnam.diceanchors.sim.engine.RunHistoryStore;
import dev.dunnam.diceanchors.sim.engine.ScenarioLoader;
import dev.dunnam.diceanchors.sim.engine.SimulationRunRecord;
import dev.dunnam.diceanchors.sim.engine.SimulationRunRecord.TurnSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Extracts per-fact survival data from an {@link ExperimentReport}.
 * <p>
 * For each scenario in the report, loads ground truth text via {@link ScenarioLoader},
 * loads run records via {@link RunHistoryStore}, extracts contradiction verdicts from
 * turn snapshots, and computes survived/total counts plus the earliest drift turn
 * per fact per condition.
 * <p>
 * This is a stateless utility class with a single static entry point. All data is
 * computed fresh on each call (no caching).
 */
public final class FactSurvivalLoader {

    private static final Logger logger = LoggerFactory.getLogger(FactSurvivalLoader.class);

    private FactSurvivalLoader() {
        // utility class
    }

    /**
     * Load per-fact survival data for every scenario in the given experiment report.
     *
     * @param report         the completed experiment report
     * @param store          run history store for loading individual run records
     * @param scenarioLoader scenario loader for resolving ground truth text
     * @return map of scenarioId to ordered list of {@link FactSurvivalRow}, one per ground truth fact
     */
    public static Map<String, List<FactSurvivalRow>> loadFactSurvival(
            ExperimentReport report, RunHistoryStore store, ScenarioLoader scenarioLoader) {

        var result = new LinkedHashMap<String, List<FactSurvivalRow>>();

        for (var scenarioId : report.scenarioIds()) {
            var factIdToText = buildGroundTruthIndex(scenarioId, scenarioLoader);
            var rows = computeSurvivalRows(report, store, scenarioId, factIdToText);
            result.put(scenarioId, rows);
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Ground truth resolution
    // -------------------------------------------------------------------------

    /**
     * Build a factId -> display text index from the scenario's ground truth list.
     * Returns an ordered map preserving the scenario's ground truth definition order.
     */
    static Map<String, String> buildGroundTruthIndex(String scenarioId, ScenarioLoader scenarioLoader) {
        var index = new LinkedHashMap<String, String>();
        try {
            var scenario = scenarioLoader.load(scenarioId);
            if (scenario.groundTruth() != null) {
                for (var fact : scenario.groundTruth()) {
                    if (fact.id() != null && fact.text() != null) {
                        index.put(fact.id(), fact.text());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load scenario {} for ground truth index: {}", scenarioId, e.getMessage());
        }
        return index;
    }

    // -------------------------------------------------------------------------
    // Run record loading
    // -------------------------------------------------------------------------

    /**
     * Load run records for a given cell key from the experiment report.
     */
    private static List<SimulationRunRecord> loadCellRecords(
            String cellKey, ExperimentReport report, RunHistoryStore store) {
        try {
            var benchReport = report.cellReports().get(cellKey);
            if (benchReport == null) {
                return List.of();
            }
            var records = new ArrayList<SimulationRunRecord>();
            for (var runId : benchReport.runIds()) {
                store.load(runId).ifPresent(records::add);
            }
            return records;
        } catch (Exception e) {
            logger.warn("Failed to load run records for cell {}: {}", cellKey, e.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // Contradiction / drift extraction
    // -------------------------------------------------------------------------

    /**
     * Collect the earliest turn number at which each factId received a
     * {@link EvalVerdict.Verdict#CONTRADICTED} verdict.
     *
     * @return map of factId to first-drift turn number; absent means never contradicted
     */
    static Map<String, Integer> collectFirstDriftTurns(SimulationRunRecord record) {
        var firstDrift = new HashMap<String, Integer>();
        for (TurnSnapshot snapshot : record.turnSnapshots()) {
            if (snapshot.verdicts() == null) {
                continue;
            }
            for (var verdict : snapshot.verdicts()) {
                if (verdict != null
                        && verdict.verdict() == EvalVerdict.Verdict.CONTRADICTED
                        && verdict.factId() != null) {
                    firstDrift.merge(verdict.factId(), snapshot.turnNumber(), Math::min);
                }
            }
        }
        return firstDrift;
    }

    /**
     * Collect fact IDs that received at least one {@link EvalVerdict.Verdict#CONFIRMED}
     * verdict across all turn snapshots in a run.
     *
     * @return set of confirmed factIds; absent means never confirmed
     */
    static Set<String> collectConfirmedFacts(SimulationRunRecord record) {
        var confirmed = new HashSet<String>();
        for (var snapshot : record.turnSnapshots()) {
            if (snapshot.verdicts() == null) continue;
            for (var verdict : snapshot.verdicts()) {
                if (verdict != null && verdict.verdict() == EvalVerdict.Verdict.CONFIRMED
                        && verdict.factId() != null) {
                    confirmed.add(verdict.factId());
                }
            }
        }
        return confirmed;
    }

    // -------------------------------------------------------------------------
    // Aggregation
    // -------------------------------------------------------------------------

    /**
     * Compute survival rows for a single scenario across all conditions.
     */
    private static List<FactSurvivalRow> computeSurvivalRows(
            ExperimentReport report, RunHistoryStore store,
            String scenarioId, Map<String, String> factIdToText) {

        // condition -> factId -> mutable stats
        var statsByCondition = new HashMap<String, Map<String, MutableFactStats>>();

        for (var condition : report.conditions()) {
            var cellKey = condition + ":" + scenarioId;
            var records = loadCellRecords(cellKey, report, store);

            var factStatsMap = new HashMap<String, MutableFactStats>();
            statsByCondition.put(condition, factStatsMap);

            for (var record : records) {
                var firstDriftTurns = collectFirstDriftTurns(record);
                var confirmedFacts = collectConfirmedFacts(record);

                for (var factId : factIdToText.keySet()) {
                    var current = factStatsMap.computeIfAbsent(factId,
                            k -> new MutableFactStats());

                    var contradicted = firstDriftTurns.containsKey(factId);
                    var confirmed = confirmedFacts.contains(factId);

                    current.total++;
                    if (confirmed && !contradicted) {
                        current.survived++;
                    } else if (contradicted) {
                        current.firstDrift = Math.min(current.firstDrift, firstDriftTurns.get(factId));
                    }
                }
            }
        }

        // Assemble rows in ground truth order
        var rows = new ArrayList<FactSurvivalRow>();
        for (var entry : factIdToText.entrySet()) {
            var factId = entry.getKey();
            var factText = entry.getValue();

            var conditionResults = new LinkedHashMap<String, FactConditionResult>();
            for (var condition : report.conditions()) {
                var condMap = statsByCondition.get(condition);
                var stats = condMap != null ? condMap.get(factId) : null;

                if (stats == null || stats.total == 0) {
                    conditionResults.put(condition, new FactConditionResult(0, 0, OptionalInt.empty()));
                } else {
                    var firstDrift = stats.firstDrift < Integer.MAX_VALUE
                            ? OptionalInt.of(stats.firstDrift)
                            : OptionalInt.empty();
                    conditionResults.put(condition, new FactConditionResult(stats.survived, stats.total, firstDrift));
                }
            }

            rows.add(new FactSurvivalRow(factId, factText, Map.copyOf(conditionResults)));
        }

        return rows;
    }

    /**
     * Mutable accumulator for per-fact stats during aggregation.
     */
    private static class MutableFactStats {
        int survived;
        int total;
        int firstDrift = Integer.MAX_VALUE;
    }
}
