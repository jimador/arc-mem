package dev.dunnam.diceanchors.anchor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("MaintenanceStrategy")
class MaintenanceStrategyTest {

    private static final MaintenanceContext CONTEXT = new MaintenanceContext(
            "test-ctx", List.of(), 1, null);

    @Nested
    @DisplayName("ReactiveMaintenanceStrategy")
    class Reactive {

        private final ReactiveMaintenanceStrategy strategy = new ReactiveMaintenanceStrategy(
                DecayPolicy.exponential(24.0), ReinforcementPolicy.threshold());

        @Test
        @DisplayName("shouldRunSweep always returns false")
        void neverSweeps() {
            assertThat(strategy.shouldRunSweep(CONTEXT)).isFalse();
        }

        @Test
        @DisplayName("executeSweep returns empty result")
        void sweepReturnsEmpty() {
            var result = strategy.executeSweep(CONTEXT);
            assertThat(result.anchorsAudited()).isZero();
            assertThat(result.anchorsPruned()).isZero();
        }

        @Test
        @DisplayName("exposes underlying policies")
        void exposesPolicies() {
            assertThat(strategy.decayPolicy()).isNotNull();
            assertThat(strategy.reinforcementPolicy()).isNotNull();
        }
    }

    @Nested
    @DisplayName("HybridMaintenanceStrategy")
    class Hybrid {

        private final ReactiveMaintenanceStrategy reactive = mock(ReactiveMaintenanceStrategy.class);
        private final ProactiveMaintenanceStrategy proactive = mock(ProactiveMaintenanceStrategy.class);
        private final HybridMaintenanceStrategy strategy = new HybridMaintenanceStrategy(reactive, proactive);

        @Test
        @DisplayName("onTurnComplete delegates to reactive")
        void turnCompleteDelegatesToReactive() {
            strategy.onTurnComplete(CONTEXT);
            verify(reactive).onTurnComplete(CONTEXT);
        }

        @Test
        @DisplayName("shouldRunSweep delegates to proactive")
        void sweepCheckDelegatesToProactive() {
            strategy.shouldRunSweep(CONTEXT);
            verify(proactive).shouldRunSweep(CONTEXT);
        }

        @Test
        @DisplayName("executeSweep delegates to proactive")
        void sweepExecutionDelegatesToProactive() {
            org.mockito.Mockito.when(proactive.executeSweep(CONTEXT)).thenReturn(SweepResult.empty());
            strategy.executeSweep(CONTEXT);
            verify(proactive).executeSweep(CONTEXT);
        }
    }

    @Nested
    @DisplayName("ProactiveMaintenanceStrategy")
    class Proactive {

        private final MemoryPressureGauge pressureGauge = mock(MemoryPressureGauge.class);
        private final ProactiveMaintenanceStrategy strategy = buildProactiveStrategy();

        private ProactiveMaintenanceStrategy buildProactiveStrategy() {
            var anchorEngine = mock(AnchorEngine.class);
            var repository = mock(dev.dunnam.diceanchors.persistence.AnchorRepository.class);
            var canonizationGate = mock(CanonizationGate.class);
            var invariantEvaluator = mock(InvariantEvaluator.class);
            var llmCallService = mock(dev.dunnam.diceanchors.sim.engine.LlmCallService.class);
            var properties = new dev.dunnam.diceanchors.DiceAnchorsProperties(
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
            return new ProactiveMaintenanceStrategy(pressureGauge, anchorEngine, repository,
                    canonizationGate, invariantEvaluator, llmCallService, properties);
        }

        @Test
        @DisplayName("shouldRunSweep returns false when pressure is null")
        void belowThresholdNeverSweeps() {
            org.mockito.Mockito.when(pressureGauge.computePressure(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn(PressureScore.zero());
            assertThat(strategy.shouldRunSweep(CONTEXT)).isFalse();
        }

        @Test
        @DisplayName("executeSweep returns non-null SweepResult")
        void sweepReturnsResult() {
            org.mockito.Mockito.when(pressureGauge.computePressure(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn(PressureScore.zero());
            var result = strategy.executeSweep(CONTEXT);
            assertThat(result).isNotNull();
        }
    }
}
