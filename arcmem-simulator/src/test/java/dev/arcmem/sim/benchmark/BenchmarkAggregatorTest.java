package dev.arcmem.simulator.benchmark;
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

import dev.arcmem.core.spi.llm.*;
import dev.arcmem.simulator.engine.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

import dev.arcmem.simulator.engine.ScoringResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("BenchmarkAggregator")
class BenchmarkAggregatorTest {

    private final BenchmarkAggregator aggregator = new BenchmarkAggregator();

    private static ScoringResult result(double survival, int contradictions, int major,
                                         double absorption, double meanDrift, int attribution,
                                         Map<String, Double> strategies) {
        return new ScoringResult(survival, contradictions, major, absorption, meanDrift, attribution, strategies);
    }

    @Nested
    @DisplayName("aggregate")
    class Aggregate {

        @Test
        @DisplayName("basic aggregation with 3 results produces correct mean, stddev, median, p95")
        void threeResultsProducesCorrectStats() {
            var results = List.of(
                    result(80, 2, 1, 90, 3.0, 5, Map.of()),
                    result(90, 3, 2, 85, 4.0, 6, Map.of()),
                    result(100, 4, 3, 80, 5.0, 7, Map.of())
            );

            var report = aggregator.aggregate(results, "test-scenario", List.of("r1", "r2", "r3"), 5000L);

            var survivalStats = report.metricStatistics().get("factSurvivalRate");
            assertThat(survivalStats.mean()).isCloseTo(90.0, within(0.001));
            // population stddev of [80, 90, 100]: sqrt(((100/3))) = 8.165
            assertThat(survivalStats.stddev()).isCloseTo(8.165, within(0.01));
            assertThat(survivalStats.median()).isEqualTo(90.0);
            assertThat(survivalStats.min()).isEqualTo(80.0);
            assertThat(survivalStats.max()).isEqualTo(100.0);
            assertThat(survivalStats.sampleCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("single run produces stddev=0")
        void singleRunStddevZero() {
            var results = List.of(
                    result(85, 1, 0, 95, 2.0, 4, Map.of())
            );

            var report = aggregator.aggregate(results, "solo", List.of("r1"), 1000L);

            var survivalStats = report.metricStatistics().get("factSurvivalRate");
            assertThat(survivalStats.mean()).isEqualTo(85.0);
            assertThat(survivalStats.stddev()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("empty input throws IllegalArgumentException")
        void emptyInputThrows() {
            assertThatThrownBy(() -> aggregator.aggregate(List.of(), "empty", List.of(), 0L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("NaN meanTurnsToFirstDrift values are filtered")
        void nanMeanTurnsToFirstDriftFiltered() {
            var results = List.of(
                    result(90, 0, 0, 100, Double.NaN, 5, Map.of()),
                    result(85, 1, 0, 90, Double.NaN, 4, Map.of()),
                    result(80, 2, 1, 80, 5.0, 3, Map.of())
            );

            var report = aggregator.aggregate(results, "nan-test", List.of("r1", "r2", "r3"), 3000L);

            var mtfdStats = report.metricStatistics().get("meanTurnsToFirstDrift");
            assertThat(mtfdStats.sampleCount()).isEqualTo(1);
            assertThat(mtfdStats.mean()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("all NaN meanTurnsToFirstDrift produces NaN stats with sampleCount=0")
        void allNanMeanTurnsToFirstDrift() {
            var results = List.of(
                    result(100, 0, 0, 100, Double.NaN, 5, Map.of()),
                    result(95, 0, 0, 100, Double.NaN, 4, Map.of())
            );

            var report = aggregator.aggregate(results, "all-nan", List.of("r1", "r2"), 2000L);

            var mtfdStats = report.metricStatistics().get("meanTurnsToFirstDrift");
            assertThat(mtfdStats.sampleCount()).isEqualTo(0);
            assertThat(mtfdStats.mean()).isNaN();
            assertThat(mtfdStats.stddev()).isNaN();
        }

        @Test
        @DisplayName("per-strategy stats are computed across runs with absent strategies defaulting to 0.0")
        void perStrategyStatsComputed() {
            var results = List.of(
                    result(80, 2, 1, 90, 3.0, 5,
                            Map.of("SUBTLE_REFRAME", 0.5, "AUTHORITY_HIJACK", 1.0)),
                    result(90, 1, 0, 95, 4.0, 6,
                            Map.of("SUBTLE_REFRAME", 0.3)),
                    result(85, 3, 2, 85, 5.0, 4,
                            Map.of("AUTHORITY_HIJACK", 0.8))
            );

            var report = aggregator.aggregate(results, "strategy-test", List.of("r1", "r2", "r3"), 4000L);

            var subtleStats = report.strategyStatistics().get("SUBTLE_REFRAME");
            assertThat(subtleStats).isNotNull();
            // Values: [0.5, 0.3, 0.0] -> mean = 0.2667
            assertThat(subtleStats.mean()).isCloseTo(0.2667, within(0.001));
            assertThat(subtleStats.sampleCount()).isEqualTo(3);

            var hijackStats = report.strategyStatistics().get("AUTHORITY_HIJACK");
            assertThat(hijackStats).isNotNull();
            // Values: [1.0, 0.0, 0.8] -> mean = 0.6
            assertThat(hijackStats.mean()).isCloseTo(0.6, within(0.001));
        }

        @Test
        @DisplayName("report contains correct scenarioId, runIds, runCount, durationMs")
        void reportMetadataCorrect() {
            var results = List.of(
                    result(80, 1, 0, 90, 3.0, 5, Map.of()),
                    result(90, 2, 1, 85, 4.0, 6, Map.of())
            );
            var runIds = List.of("run-a", "run-b");

            var report = aggregator.aggregate(results, "my-scenario", runIds, 7500L);

            assertThat(report.scenarioId()).isEqualTo("my-scenario");
            assertThat(report.runIds()).containsExactly("run-a", "run-b");
            assertThat(report.runCount()).isEqualTo(2);
            assertThat(report.totalDurationMs()).isEqualTo(7500L);
            assertThat(report.reportId()).startsWith("bench-");
            assertThat(report.createdAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("computeStats")
    class ComputeStats {

        @Test
        @DisplayName("odd count median [1,2,3] -> median=2")
        void oddCountMedian() {
            var stats = aggregator.computeStats(new double[]{1, 2, 3});
            assertThat(stats.median()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("even count median [1,2,3,4] -> median=2.5")
        void evenCountMedian() {
            var stats = aggregator.computeStats(new double[]{1, 2, 3, 4});
            assertThat(stats.median()).isEqualTo(2.5);
        }

        @Test
        @DisplayName("p95 with linear interpolation [10,20,30,40,50] -> 48.0")
        void p95LinearInterpolation() {
            var stats = aggregator.computeStats(new double[]{10, 20, 30, 40, 50});
            // index = 0.95 * 4 = 3.8, lower=3, upper=4, fraction=0.8
            // p95 = 40 + 0.8*(50-40) = 48.0
            assertThat(stats.p95()).isCloseTo(48.0, within(0.001));
        }

        @Test
        @DisplayName("single value [42] -> mean=42, stddev=0, median=42, min=max=p95=42")
        void singleValue() {
            var stats = aggregator.computeStats(new double[]{42});
            assertThat(stats.mean()).isEqualTo(42.0);
            assertThat(stats.stddev()).isEqualTo(0.0);
            assertThat(stats.median()).isEqualTo(42.0);
            assertThat(stats.min()).isEqualTo(42.0);
            assertThat(stats.max()).isEqualTo(42.0);
            assertThat(stats.p95()).isEqualTo(42.0);
            assertThat(stats.sampleCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("computeDeltas")
    class ComputeDeltas {

        @Test
        @DisplayName("deltas are current.mean - baseline.mean for each metric")
        void deltasAreCurrentMinusBaseline() {
            var currentStats = Map.of(
                    "factSurvivalRate", new BenchmarkStatistics(90, 5, 80, 100, 90, 98, 3),
                    "contradictionCount", new BenchmarkStatistics(2, 1, 1, 3, 2, 3, 3)
            );
            var baselineStats = Map.of(
                    "factSurvivalRate", new BenchmarkStatistics(80, 4, 70, 90, 80, 88, 3),
                    "contradictionCount", new BenchmarkStatistics(5, 2, 3, 7, 5, 7, 3)
            );

            var current = new BenchmarkReport("r1", "s1", java.time.Instant.now(), 3, 1000,
                    currentStats, Map.of(), List.of(), null, null);
            var baseline = new BenchmarkReport("r0", "s1", java.time.Instant.now(), 3, 1000,
                    baselineStats, Map.of(), List.of(), null, null);

            var deltas = aggregator.computeDeltas(current, baseline);

            assertThat(deltas.get("factSurvivalRate")).isEqualTo(10.0);
            assertThat(deltas.get("contradictionCount")).isEqualTo(-3.0);
        }

        @Test
        @DisplayName("missing metric in baseline is not included in deltas")
        void missingMetricInBaselineExcluded() {
            var currentStats = Map.of(
                    "factSurvivalRate", new BenchmarkStatistics(90, 5, 80, 100, 90, 98, 3),
                    "newMetric", new BenchmarkStatistics(50, 2, 40, 60, 50, 58, 3)
            );
            var baselineStats = Map.of(
                    "factSurvivalRate", new BenchmarkStatistics(80, 4, 70, 90, 80, 88, 3)
            );

            var current = new BenchmarkReport("r1", "s1", java.time.Instant.now(), 3, 1000,
                    currentStats, Map.of(), List.of(), null, null);
            var baseline = new BenchmarkReport("r0", "s1", java.time.Instant.now(), 3, 1000,
                    baselineStats, Map.of(), List.of(), null, null);

            var deltas = aggregator.computeDeltas(current, baseline);

            assertThat(deltas).containsKey("factSurvivalRate");
            assertThat(deltas).doesNotContainKey("newMetric");
        }
    }

    @Nested
    @DisplayName("BenchmarkStatistics")
    class StatisticsTests {

        @Test
        @DisplayName("coefficientOfVariation with mean=100, stddev=60 -> CV=0.6")
        void coefficientOfVariationNormal() {
            var stats = new BenchmarkStatistics(100, 60, 40, 160, 100, 155, 10);
            assertThat(stats.coefficientOfVariation()).isCloseTo(0.6, within(0.001));
        }

        @Test
        @DisplayName("coefficientOfVariation with mean=0 -> CV=0.0 (zero-mean safety)")
        void coefficientOfVariationZeroMean() {
            var stats = new BenchmarkStatistics(0, 5, -5, 5, 0, 4.5, 10);
            assertThat(stats.coefficientOfVariation()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("isHighVariance true when CV > 0.5")
        void isHighVarianceTrue() {
            var stats = new BenchmarkStatistics(100, 60, 40, 160, 100, 155, 10);
            assertThat(stats.isHighVariance()).isTrue();
        }

        @Test
        @DisplayName("isHighVariance false when CV <= 0.5")
        void isHighVarianceFalse() {
            var stats = new BenchmarkStatistics(100, 50, 50, 150, 100, 145, 10);
            assertThat(stats.isHighVariance()).isFalse();
        }
    }
}
