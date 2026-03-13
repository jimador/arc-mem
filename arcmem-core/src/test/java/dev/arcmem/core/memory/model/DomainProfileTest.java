package dev.arcmem.core.memory.model;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DomainProfile")
class DomainProfileTest {

    @Nested
    @DisplayName("constructor weight validation")
    class ConstructorWeightValidation {

        @Test
        @DisplayName("valid weights summing to 1.0 are accepted without exception")
        void validWeightsSumToOneAccepted() {
            assertThatNoException().isThrownBy(() ->
                    new DomainProfile("test",
                            Map.of("a", 0.5, "b", 0.5),
                            0.7, 0.4, 0.2));
        }

        @Test
        @DisplayName("invalid weights summing to 0.8 are rejected with IllegalArgumentException containing weight sum")
        void invalidWeightsSumRejected() {
            assertThatThrownBy(() ->
                    new DomainProfile("bad",
                            Map.of("a", 0.4, "b", 0.4),
                            0.7, 0.4, 0.2))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("0.8");
        }
    }

    @Nested
    @DisplayName("predefined profiles")
    class PredefinedProfiles {

        @Test
        @DisplayName("BALANCED profile has equal weights summing to 1.0")
        void balancedProfileHasEqualWeights() {
            var sum = DomainProfile.BALANCED.weights().values().stream().mapToDouble(Double::doubleValue).sum();
            assertThat(sum).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001));
        }
    }
}
