package dev.arcmem.core.assembly.compaction;
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CompactionConfig — Guardrail Validation")
class CompactionConfigTest {

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

}
