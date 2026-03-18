package dev.arcmem.simulator.reevaluation;

import dev.arcmem.simulator.evaluation.DriftReEvaluator;
import dev.arcmem.simulator.evaluation.JudgeConfig;
import dev.arcmem.simulator.engine.ScoringService;
import dev.arcmem.simulator.history.RunHistoryStore;
import dev.arcmem.simulator.history.SimulationRunRecord;
import dev.arcmem.simulator.history.SimulationRunRecord.TurnSnapshot;
import dev.arcmem.simulator.scenario.ScenarioLoader;
import dev.arcmem.simulator.scenario.SimulationScenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReEvaluationRunner {

    private static final Logger logger = LoggerFactory.getLogger(ReEvaluationRunner.class);

    private final RunHistoryStore runHistoryStore;
    private final ScenarioLoader scenarioLoader;
    private final DriftReEvaluator driftReEvaluator;
    private final ScoringService scoringService;

    public ReEvaluationRunner(RunHistoryStore runHistoryStore,
                              ScenarioLoader scenarioLoader,
                              DriftReEvaluator driftReEvaluator,
                              ScoringService scoringService) {
        this.runHistoryStore = runHistoryStore;
        this.scenarioLoader = scenarioLoader;
        this.driftReEvaluator = driftReEvaluator;
        this.scoringService = scoringService;
    }

    public ReEvaluationReport reEvaluate(String experimentReportId, JudgeConfig judgeConfig) {
        var report = runHistoryStore.loadExperimentReport(experimentReportId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Experiment report not found: " + experimentReportId));

        var scenarioCache = scenarioLoader.listScenarios().stream()
                .collect(Collectors.toMap(SimulationScenario::id, Function.identity()));

        int totalRuns = report.cellReports().values().stream()
                .mapToInt(cell -> cell.runIds().size())
                .sum();

        var pairedResults = new ArrayList<PairedRunResult>();
        int index = 0;

        for (var entry : report.cellReports().entrySet()) {
            var parts = entry.getKey().split(":", 2);
            var condition = parts[0];
            var scenarioId = parts[1];

            for (var runId : entry.getValue().runIds()) {
                index++;
                logger.info("Re-evaluating run {}/{}: {} [{}]", index, totalRuns, runId, condition);

                var recordOpt = runHistoryStore.load(runId);
                if (recordOpt.isEmpty()) {
                    logger.warn("Run record not found, skipping: {}", runId);
                    continue;
                }

                var record = recordOpt.get();
                var scenario = scenarioCache.get(record.scenarioId());
                var groundTruth = scenario.groundTruth();

                var modifiedSnapshots = record.turnSnapshots().stream()
                        .map(snapshot -> reEvaluateSnapshot(snapshot, groundTruth, judgeConfig))
                        .toList();

                var newScoringResult = scoringService.score(modifiedSnapshots, groundTruth);
                pairedResults.add(new PairedRunResult(
                        runId, condition, scenarioId,
                        record.scoringResult(), newScoringResult));
            }
        }

        return new ReEvaluationReport(
                experimentReportId, judgeConfig, Instant.now(), List.copyOf(pairedResults));
    }

    private TurnSnapshot reEvaluateSnapshot(TurnSnapshot snapshot,
                                            List<SimulationScenario.GroundTruth> groundTruth,
                                            JudgeConfig judgeConfig) {
        if (!snapshot.turnType().requiresEvaluation() || groundTruth.isEmpty()) {
            return snapshot;
        }

        var newVerdicts = driftReEvaluator.evaluate(
                snapshot.dmResponse(), groundTruth,
                snapshot.playerMessage(), snapshot.activeUnits(), judgeConfig);

        return new TurnSnapshot(
                snapshot.turnNumber(), snapshot.turnType(), snapshot.attackStrategies(),
                snapshot.playerMessage(), snapshot.dmResponse(), snapshot.activeUnits(),
                snapshot.contextTrace(), newVerdicts,
                snapshot.injectionEnabled(), snapshot.compactionResult());
    }
}
