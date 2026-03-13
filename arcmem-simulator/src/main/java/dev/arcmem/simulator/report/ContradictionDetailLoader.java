package dev.arcmem.simulator.report;
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

import dev.arcmem.simulator.benchmark.ExperimentReport;
import dev.arcmem.simulator.engine.EvalVerdict;
import dev.arcmem.simulator.history.RunHistoryStore;
import dev.arcmem.simulator.scenario.ScenarioLoader;
import dev.arcmem.simulator.history.SimulationRunRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts per-fact contradiction details from an {@link ExperimentReport}.
 * <p>
 * For each scenario, loads run records via {@link RunHistoryStore}, scans turn
 * snapshots for {@link EvalVerdict.Verdict#CONTRADICTED} verdicts, and groups
 * the resulting {@link ContradictionDetail} entries by fact ID.
 * <p>
 * Stateless utility class following the {@link FactSurvivalLoader} pattern.
 */
public final class ContradictionDetailLoader {

    private static final Logger logger = LoggerFactory.getLogger(ContradictionDetailLoader.class);

    private ContradictionDetailLoader() {
        // utility class
    }

    /**
     * Load per-fact contradiction details for every scenario in the given experiment report.
     *
     * @return map of scenarioId to ordered list of {@link FactContradictionGroup}
     */
    public static Map<String, List<FactContradictionGroup>> loadContradictionDetails(
            ExperimentReport report, RunHistoryStore store, ScenarioLoader scenarioLoader) {

        var result = new LinkedHashMap<String, List<FactContradictionGroup>>();

        for (var scenarioId : report.scenarioIds()) {
            var factIdToText = FactSurvivalLoader.buildGroundTruthIndex(scenarioId, scenarioLoader);
            var groups = collectContradictions(report, store, scenarioId, factIdToText);
            result.put(scenarioId, groups);
        }

        return result;
    }

    private static List<FactContradictionGroup> collectContradictions(
            ExperimentReport report, RunHistoryStore store,
            String scenarioId, Map<String, String> factIdToText) {

        var detailsByFact = new LinkedHashMap<String, List<ContradictionDetail>>();

        for (var condition : report.conditions()) {
            var cellKey = condition + ":" + scenarioId;
            var benchReport = report.cellReports().get(cellKey);
            if (benchReport == null) {
                continue;
            }

            var runIds = benchReport.runIds();
            for (int runIdx = 0; runIdx < runIds.size(); runIdx++) {
                var runId = runIds.get(runIdx);
                var recordOpt = store.load(runId);
                if (recordOpt.isEmpty()) {
                    continue;
                }

                var record = recordOpt.get();
                extractFromRun(record, condition, runIdx + 1, detailsByFact);
            }
        }

        var groups = new ArrayList<FactContradictionGroup>();
        for (var entry : factIdToText.entrySet()) {
            var factId = entry.getKey();
            var details = detailsByFact.get(factId);
            if (details != null && !details.isEmpty()) {
                details.sort(Comparator.comparing(ContradictionDetail::condition)
                                       .thenComparingInt(ContradictionDetail::runIndex)
                                       .thenComparingInt(ContradictionDetail::turnNumber));
                groups.add(new FactContradictionGroup(factId, entry.getValue(), List.copyOf(details)));
            }
        }

        return List.copyOf(groups);
    }

    private static void extractFromRun(
            SimulationRunRecord record, String condition, int runIndex,
            Map<String, List<ContradictionDetail>> detailsByFact) {

        for (var snapshot : record.turnSnapshots()) {
            if (snapshot.verdicts() == null) {
                continue;
            }
            for (var verdict : snapshot.verdicts()) {
                if (verdict != null
                    && verdict.verdict() == EvalVerdict.Verdict.CONTRADICTED
                    && verdict.factId() != null) {

                    var detail = ContradictionDetail.of(
                            verdict.factId(),
                            condition,
                            runIndex,
                            snapshot.turnNumber(),
                            snapshot.attackStrategies(),
                            snapshot.playerMessage(),
                            snapshot.dmResponse(),
                            verdict.severity(),
                            verdict.explanation());

                    detailsByFact.computeIfAbsent(verdict.factId(), k -> new ArrayList<>())
                                 .add(detail);
                }
            }
        }
    }
}
