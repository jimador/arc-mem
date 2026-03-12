package dev.dunnam.diceanchors.sim.engine;

import dev.dunnam.diceanchors.sim.benchmark.BenchmarkReport;
import dev.dunnam.diceanchors.sim.benchmark.ExperimentReport;

import java.util.List;
import java.util.Optional;

/**
 * SPI for persisting simulation run records, benchmark reports, and experiment reports.
 * Implementations MUST be thread-safe.
 */
public interface RunHistoryStore {

    void save(SimulationRunRecord record);

    Optional<SimulationRunRecord> load(String runId);

    List<SimulationRunRecord> list();

    List<SimulationRunRecord> listByScenario(String scenarioId);

    void delete(String runId);

    void saveBenchmarkReport(BenchmarkReport report);

    Optional<BenchmarkReport> loadBenchmarkReport(String reportId);

    List<BenchmarkReport> listBenchmarkReports();

    List<BenchmarkReport> listBenchmarkReportsByScenario(String scenarioId);

    void saveAsBaseline(String reportId, String scenarioId);

    Optional<BenchmarkReport> loadBaseline(String scenarioId);

    void deleteBenchmarkReport(String reportId);

    void saveExperimentReport(ExperimentReport report);

    Optional<ExperimentReport> loadExperimentReport(String reportId);

    List<ExperimentReport> listExperimentReports();

    void deleteExperimentReport(String reportId);
}
