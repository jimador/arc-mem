package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.event.AnchorLifecycleEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@DisplayName("Memory Pressure Gauge")
class MemoryPressureGaugeTest {

    private MemoryPressureGauge gauge;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        var config = new DiceAnchorsProperties.PressureConfig(
                true, 0.4, 0.3, 0.2, 0.1, 0.4, 0.8, 1.5, 5
        );
        var props = new DiceAnchorsProperties(
                null, null, null, null, null, null, null, null, null, null, null, null, config, null, null
        );
        gauge = new MemoryPressureGauge(eventPublisher, props);
    }

    @Nested
    @DisplayName("CompositeComputation")
    class CompositeComputationTests {

        @Test
        @DisplayName("noEvents_returnsOnlyBudgetContribution")
        void noEventsReturnsOnlyBudgetContribution() {
            var score = gauge.computePressure("ctx-1", 15, 20);

            assertThat(score.total()).isGreaterThan(0.0);
            assertThat(score.budget()).isGreaterThan(0.0);
            assertThat(score.conflict()).isEqualTo(0.0);
            assertThat(score.decay()).isEqualTo(0.0);
            assertThat(score.compaction()).isEqualTo(0.0);

            double expectedBudgetRaw = Math.pow(15.0 / 20.0, 1.5);
            double expectedBudgetWeighted = expectedBudgetRaw * 0.4;
            assertThat(score.budget()).isCloseTo(expectedBudgetWeighted, org.assertj.core.api.Assertions.within(0.01));
            assertThat(score.total()).isCloseTo(expectedBudgetWeighted, org.assertj.core.api.Assertions.within(0.01));
        }

        @Test
        @DisplayName("allDimensionsActive_returnsWeightedSum")
        void allDimensionsActiveReturnsWeightedSum() {
            gauge.onConflictDetected(AnchorLifecycleEvent.conflictDetected(this, "ctx-1", "test", 1, List.of("a1")));
            gauge.onConflictDetected(AnchorLifecycleEvent.conflictDetected(this, "ctx-1", "test", 1, List.of("a2")));
            gauge.onAuthorityChanged(AnchorLifecycleEvent.authorityChanged(this, "ctx-1", "a1",
                    Authority.RELIABLE, Authority.UNRELIABLE, AuthorityChangeDirection.DEMOTED, "test"));
            gauge.recordCompaction("ctx-1");

            var score = gauge.computePressure("ctx-1", 18, 20);

            assertThat(score.conflict()).isGreaterThan(0.0);
            assertThat(score.decay()).isGreaterThan(0.0);
            assertThat(score.compaction()).isGreaterThan(0.0);
            assertThat(score.total()).isGreaterThan(0.0);

            double sum = score.budget() + score.conflict() + score.decay() + score.compaction();
            assertThat(score.total()).isCloseTo(sum, org.assertj.core.api.Assertions.within(0.01));
        }

        @Test
        @DisplayName("sameInputs_returnsDeterministicResult")
        void sameInputsDeterministicResult() {
            var score1 = gauge.computePressure("ctx-1", 15, 20);
            gauge.clearContext("ctx-1");
            var score2 = gauge.computePressure("ctx-1", 15, 20);

            assertThat(score1.total()).isEqualTo(score2.total());
            assertThat(score1.budget()).isEqualTo(score2.budget());
        }

        @Test
        @DisplayName("totalClampedToOne")
        void totalClampedToOne() {
            gauge.onConflictDetected(AnchorLifecycleEvent.conflictDetected(this, "ctx-1", "test", 1, List.of("a1")));
            gauge.onConflictDetected(AnchorLifecycleEvent.conflictDetected(this, "ctx-1", "test", 1, List.of("a2")));
            gauge.onConflictDetected(AnchorLifecycleEvent.conflictDetected(this, "ctx-1", "test", 1, List.of("a3")));
            gauge.onConflictDetected(AnchorLifecycleEvent.conflictDetected(this, "ctx-1", "test", 1, List.of("a4")));
            gauge.onConflictDetected(AnchorLifecycleEvent.conflictDetected(this, "ctx-1", "test", 1, List.of("a5")));

            var score = gauge.computePressure("ctx-1", 20, 20);

            assertThat(score.total()).isLessThanOrEqualTo(1.0);
            assertThat(score.total()).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("BudgetPressure")
    class BudgetPressureTests {

        @Test
        @DisplayName("zeroAnchors_returnsZero")
        void zeroAnchorsReturnsZero() {
            var score = gauge.computePressure("ctx-1", 0, 20);

            assertThat(score.budget()).isEqualTo(0.0);
            assertThat(score.total()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("fullCapacity_returnsOne")
        void fullCapacityReturnsOne() {
            var score = gauge.computePressure("ctx-1", 20, 20);

            assertThat(score.budget()).isEqualTo(0.4);
            assertThat(score.total()).isEqualTo(0.4);
        }

        @Test
        @DisplayName("nonLinearExponent_appliesCorrectly")
        void nonLinearExponentAppliesCorrectly() {
            var score = gauge.computePressure("ctx-1", 18, 20);

            double rawBudget = Math.pow(18.0 / 20.0, 1.5);
            double expectedWeighted = rawBudget * 0.4;

            assertThat(score.budget()).isCloseTo(expectedWeighted, org.assertj.core.api.Assertions.within(0.01));
        }
    }

    @Nested
    @DisplayName("ThresholdBreaches")
    class ThresholdBreachesTests {

        @Test
        @DisplayName("crossesLightSweep_publishesEvent")
        void crossesLightSweepPublishesEvent() {
            for (int i = 0; i < 5; i++) {
                gauge.onConflictDetected(AnchorLifecycleEvent.conflictDetected(this, "ctx-1", "test", 1, List.of("a" + i)));
            }

            gauge.computePressure("ctx-1", 10, 20);

            ArgumentCaptor<AnchorLifecycleEvent> captor = ArgumentCaptor.forClass(AnchorLifecycleEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            var event = captor.getValue();
            assertThat(event).isInstanceOf(AnchorLifecycleEvent.PressureThresholdBreached.class);

            var breachEvent = (AnchorLifecycleEvent.PressureThresholdBreached) event;
            assertThat(breachEvent.getThresholdType()).isEqualTo("LIGHT_SWEEP");
            assertThat(breachEvent.getContextId()).isEqualTo("ctx-1");
        }

        @Test
        @DisplayName("remainsAboveThreshold_doesNotRepublish")
        void remainsAboveThresholdDoesNotRepublish() {
            for (int i = 0; i < 5; i++) {
                gauge.onConflictDetected(AnchorLifecycleEvent.conflictDetected(this, "ctx-1", "test", 1, List.of("a" + i)));
            }

            gauge.computePressure("ctx-1", 10, 20);

            org.mockito.Mockito.reset(eventPublisher);

            for (int i = 0; i < 3; i++) {
                gauge.onConflictDetected(AnchorLifecycleEvent.conflictDetected(this, "ctx-1", "test", 1, List.of("b" + i)));
            }

            gauge.computePressure("ctx-1", 10, 20);

            org.mockito.Mockito.verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("dropsAndRises_republishesEvent")
        void dropsAndRisesRepublishesEvent() {
            for (int i = 0; i < 5; i++) {
                gauge.onConflictDetected(AnchorLifecycleEvent.conflictDetected(this, "ctx-1", "test", 1, List.of("a" + i)));
            }
            gauge.computePressure("ctx-1", 10, 20);

            org.mockito.Mockito.reset(eventPublisher);

            gauge.clearContext("ctx-1");
            gauge.computePressure("ctx-1", 1, 20);

            org.mockito.Mockito.verifyNoInteractions(eventPublisher);

            for (int i = 0; i < 5; i++) {
                gauge.onConflictDetected(AnchorLifecycleEvent.conflictDetected(this, "ctx-1", "test", 1, List.of("c" + i)));
            }
            gauge.computePressure("ctx-1", 10, 20);

            ArgumentCaptor<AnchorLifecycleEvent> captor = ArgumentCaptor.forClass(AnchorLifecycleEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            var event = captor.getValue();
            assertThat(event).isInstanceOf(AnchorLifecycleEvent.PressureThresholdBreached.class);
        }
    }

    @Nested
    @DisplayName("History")
    class HistoryTests {

        @Test
        @DisplayName("multipleEvaluations_returnsChronologicalOrder")
        void multipleEvaluationsReturnsChronologicalOrder() {
            gauge.computePressure("ctx-1", 5, 20);
            gauge.computePressure("ctx-1", 10, 20);
            gauge.computePressure("ctx-1", 15, 20);

            var history = gauge.getHistory("ctx-1");

            assertThat(history).hasSize(3);
            assertThat(history.get(0).budget()).isLessThan(history.get(1).budget());
            assertThat(history.get(1).budget()).isLessThan(history.get(2).budget());
        }

        @Test
        @DisplayName("clearContext_removesAllState")
        void clearContextRemovesAllState() {
            gauge.computePressure("ctx-1", 10, 20);
            gauge.recordCompaction("ctx-1");

            gauge.clearContext("ctx-1");

            var history = gauge.getHistory("ctx-1");
            assertThat(history).isEmpty();

            var newScore = gauge.computePressure("ctx-1", 10, 20);
            assertThat(newScore.compaction()).isEqualTo(0.0);
        }
    }
}
