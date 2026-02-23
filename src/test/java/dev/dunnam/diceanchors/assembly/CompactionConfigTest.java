package dev.dunnam.diceanchors.assembly;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CompactionConfig — Guardrail Validation (Section 8.1)")
class CompactionConfigTest {

    @Nested
    @DisplayName("valid construction")
    class ValidConstruction {

        @Test
        @DisplayName("accepts all valid fields including guardrail parameters")
        void validFieldsConstructsSuccessfully() {
            var config = new CompactionConfig(true, 500, 10, List.of(3, 7), 0.6, 3, 2000L, true);

            assertThat(config.enabled()).isTrue();
            assertThat(config.tokenThreshold()).isEqualTo(500);
            assertThat(config.messageThreshold()).isEqualTo(10);
            assertThat(config.forceAtTurns()).containsExactly(3, 7);
            assertThat(config.minMatchRatio()).isEqualTo(0.6);
            assertThat(config.maxRetries()).isEqualTo(3);
            assertThat(config.retryBackoffMillis()).isEqualTo(2000L);
            assertThat(config.eventsEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("minMatchRatio validation")
    class MinMatchRatioValidation {

        @Test
        @DisplayName("throws IllegalArgumentException when minMatchRatio < 0.0")
        void minMatchRatioBelowZeroThrows() {
            assertThatThrownBy(() -> new CompactionConfig(true, 0, 0, List.of(), -0.1, 2, 1000L, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("minMatchRatio");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when minMatchRatio > 1.0")
        void minMatchRatioAboveOneThrows() {
            assertThatThrownBy(() -> new CompactionConfig(true, 0, 0, List.of(), 1.1, 2, 1000L, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("minMatchRatio");
        }

        @Test
        @DisplayName("accepts minMatchRatio = 0.0 as valid lower bound")
        void minMatchRatioZeroIsValid() {
            var config = new CompactionConfig(true, 0, 0, List.of(), 0.0, 2, 1000L, true);

            assertThat(config.minMatchRatio()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("accepts minMatchRatio = 1.0 as valid upper bound")
        void minMatchRatioOneIsValid() {
            var config = new CompactionConfig(true, 0, 0, List.of(), 1.0, 2, 1000L, true);

            assertThat(config.minMatchRatio()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("maxRetries validation")
    class MaxRetriesValidation {

        @Test
        @DisplayName("throws IllegalArgumentException when maxRetries < 0")
        void maxRetriesNegativeThrows() {
            assertThatThrownBy(() -> new CompactionConfig(true, 0, 0, List.of(), 0.5, -1, 1000L, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxRetries");
        }

        @Test
        @DisplayName("accepts maxRetries = 0 as valid (single attempt, no retry)")
        void maxRetriesZeroIsValid() {
            var config = new CompactionConfig(true, 0, 0, List.of(), 0.5, 0, 1000L, true);

            assertThat(config.maxRetries()).isZero();
        }
    }

    @Nested
    @DisplayName("retryBackoffMillis validation")
    class RetryBackoffMillisValidation {

        @Test
        @DisplayName("throws IllegalArgumentException when retryBackoffMillis <= 0")
        void retryBackoffZeroThrows() {
            assertThatThrownBy(() -> new CompactionConfig(true, 0, 0, List.of(), 0.5, 2, 0L, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("retryBackoffMillis");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when retryBackoffMillis is negative")
        void retryBackoffNegativeThrows() {
            assertThatThrownBy(() -> new CompactionConfig(true, 0, 0, List.of(), 0.5, 2, -500L, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("retryBackoffMillis");
        }
    }

    @Nested
    @DisplayName("disabled() factory")
    class DisabledFactory {

        @Test
        @DisplayName("returns expected guardrail defaults")
        void disabledReturnsExpectedDefaults() {
            var config = CompactionConfig.disabled();

            assertThat(config.enabled()).isFalse();
            assertThat(config.minMatchRatio()).isEqualTo(0.5);
            assertThat(config.maxRetries()).isEqualTo(2);
            assertThat(config.retryBackoffMillis()).isEqualTo(1000L);
            assertThat(config.eventsEnabled()).isTrue();
        }
    }
}
