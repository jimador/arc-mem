package dev.arcmem.simulator.history;
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

import dev.arcmem.simulator.engine.*;

import dev.arcmem.simulator.benchmark.BenchmarkReport;
import dev.arcmem.simulator.benchmark.ExperimentReport;

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
