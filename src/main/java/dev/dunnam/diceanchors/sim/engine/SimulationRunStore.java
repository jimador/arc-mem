package dev.dunnam.diceanchors.sim.engine;

import dev.dunnam.diceanchors.sim.benchmark.BenchmarkReport;
import dev.dunnam.diceanchors.sim.benchmark.ExperimentReport;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory LRU store for completed simulation run records.
 * Evicts oldest entries when capacity is exceeded.
 * Thread-safe via synchronized access to the backing map.
 */
public class SimulationRunStore implements RunHistoryStore {

    private static final int MAX_ENTRIES = 50;

    private final Map<String, BenchmarkReport> benchmarkReports = new HashMap<>();
    private final Map<String, String> scenarioBaselines = new HashMap<>();
    private final Map<String, ExperimentReport> experimentReports = new HashMap<>();

    private final Map<String, SimulationRunRecord> store = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, SimulationRunRecord> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    @Override
    public synchronized void save(SimulationRunRecord record) {
        store.put(record.runId(), record);
    }

    @Override
    public synchronized Optional<SimulationRunRecord> load(String runId) {
        return Optional.ofNullable(store.get(runId));
    }

    /**
     * @deprecated Use {@link #load(String)} instead.
     */
    @Deprecated(forRemoval = true)
    public Optional<SimulationRunRecord> get(String runId) {
        return load(runId);
    }

    @Override
    public synchronized List<SimulationRunRecord> list() {
        return List.copyOf(store.values());
    }

    @Override
    public synchronized List<SimulationRunRecord> listByScenario(String scenarioId) {
        return store.values().stream()
                    .filter(r -> r.scenarioId().equals(scenarioId))
                    .toList();
    }

    @Override
    public synchronized void delete(String runId) {
        store.remove(runId);
    }

    public synchronized int size() {
        return store.size();
    }

    @Override
    public synchronized void saveBenchmarkReport(BenchmarkReport report) {
        benchmarkReports.put(report.reportId(), report);
    }

    @Override
    public synchronized Optional<BenchmarkReport> loadBenchmarkReport(String reportId) {
        return Optional.ofNullable(benchmarkReports.get(reportId));
    }

    @Override
    public synchronized List<BenchmarkReport> listBenchmarkReports() {
        return benchmarkReports.values().stream()
                               .sorted(Comparator.comparing(BenchmarkReport::createdAt).reversed())
                               .toList();
    }

    @Override
    public synchronized List<BenchmarkReport> listBenchmarkReportsByScenario(String scenarioId) {
        return benchmarkReports.values().stream()
                               .filter(r -> r.scenarioId().equals(scenarioId))
                               .sorted(Comparator.comparing(BenchmarkReport::createdAt).reversed())
                               .toList();
    }

    @Override
    public synchronized void saveAsBaseline(String reportId, String scenarioId) {
        if (!benchmarkReports.containsKey(reportId)) {
            return;
        }
        scenarioBaselines.put(scenarioId, reportId);
    }

    @Override
    public synchronized Optional<BenchmarkReport> loadBaseline(String scenarioId) {
        var reportId = scenarioBaselines.get(scenarioId);
        if (reportId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(benchmarkReports.get(reportId));
    }

    @Override
    public synchronized void deleteBenchmarkReport(String reportId) {
        benchmarkReports.remove(reportId);
        scenarioBaselines.values().removeIf(id -> id.equals(reportId));
    }

    @Override
    public synchronized void saveExperimentReport(ExperimentReport report) {
        experimentReports.put(report.reportId(), report);
    }

    @Override
    public synchronized Optional<ExperimentReport> loadExperimentReport(String reportId) {
        return Optional.ofNullable(experimentReports.get(reportId));
    }

    @Override
    public synchronized List<ExperimentReport> listExperimentReports() {
        return experimentReports.values().stream()
                                .sorted(Comparator.comparing(ExperimentReport::createdAt).reversed())
                                .toList();
    }

    @Override
    public synchronized void deleteExperimentReport(String reportId) {
        experimentReports.remove(reportId);
    }
}
