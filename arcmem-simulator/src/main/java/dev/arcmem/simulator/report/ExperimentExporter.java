package dev.arcmem.simulator.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.arcmem.simulator.benchmark.ConfidenceInterval;
import dev.arcmem.simulator.benchmark.ExperimentReport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeSet;

@Service
public class ExperimentExporter {

    private final ObjectMapper objectMapper;

    public ExperimentExporter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public String exportJson(ExperimentReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize experiment report to JSON", e);
        }
    }

    /**
     * Export a flattened CSV with one row per cell (condition × scenario).
     * Columns: condition, scenario, run_count, then for each metric:
     * metric_mean, metric_stddev, metric_ci_lower, metric_ci_upper.
     */
    public String exportCsv(ExperimentReport report) {
        var metricNames = new ArrayList<String>();
        report.cellReports().values().stream().findFirst().ifPresent(cell ->
                metricNames.addAll(cell.metricStatistics().keySet()));

        var sb = new StringBuilder();
        sb.append("condition,scenario,run_count");
        for (var metric : metricNames) {
            sb.append(",").append(metric).append("_mean");
            sb.append(",").append(metric).append("_stddev");
            sb.append(",").append(metric).append("_ci_lower");
            sb.append(",").append(metric).append("_ci_upper");
        }
        sb.append("\n");

        var sortedKeys = new TreeSet<>(report.cellReports().keySet());
        for (var cellKey : sortedKeys) {
            var parts = cellKey.split(":", 2);
            var condition = parts.length > 0 ? parts[0] : cellKey;
            var scenario = parts.length > 1 ? parts[1] : "";
            var cell = report.cellReports().get(cellKey);
            var cellCIs = report.confidenceIntervals().getOrDefault(cellKey, Map.of());

            sb.append(escapeCsv(condition)).append(",");
            sb.append(escapeCsv(scenario)).append(",");
            sb.append(cell.runCount());

            for (var metric : metricNames) {
                var stats = cell.metricStatistics().get(metric);
                var ci = cellCIs.get(metric);
                if (stats != null) {
                    sb.append(",").append(stats.mean());
                    sb.append(",").append(stats.stddev());
                    sb.append(",").append(ci != null ? ci.lower() : stats.mean());
                    sb.append(",").append(ci != null ? ci.upper() : stats.mean());
                } else {
                    sb.append(",,,,");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
