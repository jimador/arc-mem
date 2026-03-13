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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("EffectSizeCalculator")
class EffectSizeCalculatorTest {

    private final EffectSizeCalculator calculator = new EffectSizeCalculator();

    private static BenchmarkReport cellReport(String scenarioId, double survivalMean, double survivalStddev, int n) {
        var stats = new BenchmarkStatistics(survivalMean, survivalStddev,
                survivalMean - 0.1, survivalMean + 0.1, survivalMean, survivalMean + 0.05, n);
        return new BenchmarkReport("bench-test", scenarioId, Instant.now(), n, 1000L,
                Map.of("factSurvivalRate", stats,
                        "contradictionCount", new BenchmarkStatistics(2.0, 1.0, 1.0, 3.0, 2.0, 2.9, n)),
                Map.of("SUBTLE_REFRAME", new BenchmarkStatistics(0.5, 0.1, 0.3, 0.7, 0.5, 0.65, n)),
                List.of(), null, null);
    }

    @Nested
    @DisplayName("computeEffectSizes")
    class ComputeEffectSizes {

        @Test
        @DisplayName("two conditions with different means produce positive Cohen's d")
        void differentMeansProducePositiveCohensD() {
            var cellReports = Map.of(
                    "FULL_UNITS:scenario-1", cellReport("scenario-1", 0.90, 0.05, 30),
                    "NO_UNITS:scenario-1", cellReport("scenario-1", 0.60, 0.05, 30)
            );
            var conditions = List.of(AblationCondition.FULL_UNITS, AblationCondition.NO_UNITS);

            var matrix = calculator.computeEffectSizes(cellReports, conditions);

            var entry = matrix.get("FULL_UNITS:NO_UNITS").get("factSurvivalRate");
            assertThat(entry.cohensD()).isGreaterThan(0.0);
            assertThat(entry.interpretation()).isEqualTo("large");
        }

        @Test
        @DisplayName("interpret returns negligible for |d| < 0.2")
        void interpretNegligible() {
            assertThat(EffectSizeEntry.interpret(0.15)).isEqualTo("negligible");
        }

        @Test
        @DisplayName("interpret returns small for 0.2 <= |d| < 0.5")
        void interpretSmall() {
            assertThat(EffectSizeEntry.interpret(0.35)).isEqualTo("small");
        }

        @Test
        @DisplayName("interpret returns medium for 0.5 <= |d| < 0.8")
        void interpretMedium() {
            assertThat(EffectSizeEntry.interpret(0.65)).isEqualTo("medium");
        }

        @Test
        @DisplayName("interpret returns large for |d| >= 0.8")
        void interpretLarge() {
            assertThat(EffectSizeEntry.interpret(1.2)).isEqualTo("large");
        }

        @Test
        @DisplayName("interpret uses absolute value for negative d")
        void interpretNegativeUsesAbsoluteValue() {
            assertThat(EffectSizeEntry.interpret(-0.75)).isEqualTo("medium");
        }

        @Test
        @DisplayName("CS1: effect size is symmetric in magnitude |d(A,B)| == |d(B,A)|")
        void effectSizeSymmetricInMagnitude() {
            var cellReports = Map.of(
                    "FULL_UNITS:scenario-1", cellReport("scenario-1", 0.85, 0.05, 20),
                    "NO_UNITS:scenario-1", cellReport("scenario-1", 0.70, 0.08, 20)
            );

            var matrixAB = calculator.computeEffectSizes(cellReports,
                    List.of(AblationCondition.FULL_UNITS, AblationCondition.NO_UNITS));
            var dAB = matrixAB.get("FULL_UNITS:NO_UNITS").get("factSurvivalRate").cohensD();

            // Reverse order — alphabetical key should still be "FULL_UNITS:NO_UNITS"
            var matrixBA = calculator.computeEffectSizes(cellReports,
                    List.of(AblationCondition.NO_UNITS, AblationCondition.FULL_UNITS));
            var dBA = matrixBA.get("FULL_UNITS:NO_UNITS").get("factSurvivalRate").cohensD();

            assertThat(Math.abs(dAB)).isCloseTo(Math.abs(dBA), within(1e-10));
        }

        @Test
        @DisplayName("zero variance in both conditions returns Cohen's d = 0.0")
        void zeroVarianceReturnsDZero() {
            var cellReports = Map.of(
                    "FULL_UNITS:scenario-1", cellReport("scenario-1", 0.80, 0.0, 10),
                    "NO_UNITS:scenario-1", cellReport("scenario-1", 0.80, 0.0, 10)
            );
            var conditions = List.of(AblationCondition.FULL_UNITS, AblationCondition.NO_UNITS);

            var matrix = calculator.computeEffectSizes(cellReports, conditions);

            var entry = matrix.get("FULL_UNITS:NO_UNITS").get("factSurvivalRate");
            assertThat(entry.cohensD()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("CS2: matrix contains N*(N-1)/2 entries for N conditions")
        void matrixSizeMatchesPairCount() {
            var cellReports = Map.of(
                    "FULL_UNITS:scenario-1", cellReport("scenario-1", 0.90, 0.05, 10),
                    "NO_UNITS:scenario-1", cellReport("scenario-1", 0.60, 0.05, 10),
                    "FLAT_AUTHORITY:scenario-1", cellReport("scenario-1", 0.75, 0.05, 10),
                    "NO_RANK_DIFFERENTIATION:scenario-1", cellReport("scenario-1", 0.80, 0.05, 10)
            );
            var conditions = List.of(
                    AblationCondition.FULL_UNITS,
                    AblationCondition.NO_UNITS,
                    AblationCondition.FLAT_AUTHORITY,
                    AblationCondition.NO_RANK_DIFFERENTIATION
            );

            var matrix = calculator.computeEffectSizes(cellReports, conditions);

            // 4 conditions → 4*(4-1)/2 = 6 pairs
            assertThat(matrix).hasSize(6);
        }

        @Test
        @DisplayName("low confidence flag set when coefficient of variation > 0.5")
        void lowConfidenceFlagWhenHighVariance() {
            // CV = stddev/mean = 0.6/0.8 = 0.75 > 0.5 → high variance
            var highVarianceReport = cellReport("scenario-1", 0.80, 0.60, 10);
            var cellReports = Map.of(
                    "FULL_UNITS:scenario-1", highVarianceReport,
                    "NO_UNITS:scenario-1", cellReport("scenario-1", 0.60, 0.05, 10)
            );
            var conditions = List.of(AblationCondition.FULL_UNITS, AblationCondition.NO_UNITS);

            var matrix = calculator.computeEffectSizes(cellReports, conditions);

            var entry = matrix.get("FULL_UNITS:NO_UNITS").get("factSurvivalRate");
            assertThat(entry.lowConfidence()).isTrue();
        }
    }

    @Nested
    @DisplayName("computeConfidenceIntervals")
    class ComputeConfidenceIntervals {

        @Test
        @DisplayName("CI bounds are approximately correct for known mean, stddev, n")
        void ciBoundsApproximatelyCorrect() {
            // mean=0.85, popStddev=0.05, n=10
            // sampleSd = 0.05 * sqrt(10/9) = 0.05 * 1.05409 ≈ 0.052705
            // margin = 1.96 * 0.052705 / sqrt(10) = 1.96 * 0.016667 ≈ 0.032667
            // lower ≈ 0.85 - 0.032667 ≈ 0.81733
            // upper ≈ 0.85 + 0.032667 ≈ 0.88267
            var cellReports = Map.of(
                    "FULL_UNITS:scenario-1", cellReport("scenario-1", 0.85, 0.05, 10)
            );

            var intervals = calculator.computeConfidenceIntervals(cellReports);

            var ci = intervals.get("FULL_UNITS:scenario-1").get("factSurvivalRate");
            var sampleSd = 0.05 * Math.sqrt(10.0 / 9.0);
            var expectedMargin = 1.96 * sampleSd / Math.sqrt(10);
            assertThat(ci.lower()).isCloseTo(0.85 - expectedMargin, within(1e-6));
            assertThat(ci.upper()).isCloseTo(0.85 + expectedMargin, within(1e-6));
        }
    }

    @Nested
    @DisplayName("computeStrategyDeltas")
    class ComputeStrategyDeltas {

        @Test
        @DisplayName("strategy deltas across conditions contain correct mean values")
        void strategyDeltasContainCorrectMeans() {
            var fullReport = new BenchmarkReport("bench-test", "scenario-1", Instant.now(), 10, 1000L,
                    Map.of("factSurvivalRate", new BenchmarkStatistics(0.9, 0.05, 0.8, 1.0, 0.9, 0.95, 10)),
                    Map.of("SUBTLE_REFRAME", new BenchmarkStatistics(0.7, 0.1, 0.5, 0.9, 0.7, 0.85, 10)),
                    List.of(), null, null);
            var noReport = new BenchmarkReport("bench-test", "scenario-1", Instant.now(), 10, 1000L,
                    Map.of("factSurvivalRate", new BenchmarkStatistics(0.5, 0.05, 0.4, 0.6, 0.5, 0.55, 10)),
                    Map.of("SUBTLE_REFRAME", new BenchmarkStatistics(0.3, 0.1, 0.1, 0.5, 0.3, 0.45, 10)),
                    List.of(), null, null);

            var cellReports = Map.of(
                    "FULL_UNITS:scenario-1", fullReport,
                    "NO_UNITS:scenario-1", noReport
            );

            var deltas = calculator.computeStrategyDeltas(cellReports);

            assertThat(deltas).containsKey("SUBTLE_REFRAME");
            assertThat(deltas.get("SUBTLE_REFRAME").get("FULL_UNITS")).isCloseTo(0.7, within(1e-6));
            assertThat(deltas.get("SUBTLE_REFRAME").get("NO_UNITS")).isCloseTo(0.3, within(1e-6));
        }
    }

    @Nested
    @DisplayName("toSampleStddev")
    class ToSampleStddev {

        @Test
        @DisplayName("population to sample stddev conversion matches expected value")
        void populationToSampleConversion() {
            // popStddev=8.165, n=3 → sampleStddev = 8.165 * sqrt(3/2) ≈ 10.0
            var result = EffectSizeCalculator.toSampleStddev(8.165, 3);
            assertThat(result).isCloseTo(10.0, within(0.01));
        }

        @Test
        @DisplayName("n < 2 returns unchanged population stddev")
        void nLessThanTwoReturnsUnchanged() {
            assertThat(EffectSizeCalculator.toSampleStddev(5.0, 1)).isEqualTo(5.0);
        }
    }
}
