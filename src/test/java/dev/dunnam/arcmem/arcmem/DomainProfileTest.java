package dev.dunnam.diceanchors.anchor;

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
    @DisplayName("forTesting factory")
    class ForTestingFactory {

        @Test
        @DisplayName("forTesting() bypasses weight validation and normalizes weights")
        void forTestingBypassesValidation() {
            // Weights sum to 0.6, not 1.0 — would fail in the canonical constructor
            assertThatNoException().isThrownBy(() ->
                    DomainProfile.forTesting("test",
                            Map.of("a", 0.3, "b", 0.3),
                            0.7, 0.4, 0.2));
        }

        @Test
        @DisplayName("forTesting() returns a DomainProfile with normalized weights summing to 1.0")
        void forTestingNormalizesWeights() {
            // Weights: a=1.0, b=1.0 -> sum=2.0 -> normalized: a=0.5, b=0.5
            var profile = DomainProfile.forTesting("test",
                    Map.of("a", 1.0, "b", 1.0),
                    0.7, 0.4, 0.2);

            var sum = profile.weights().values().stream().mapToDouble(Double::doubleValue).sum();
            assertThat(sum).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001));
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
