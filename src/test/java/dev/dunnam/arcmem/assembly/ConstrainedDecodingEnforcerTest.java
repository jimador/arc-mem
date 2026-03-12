package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.MemoryTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConstrainedDecodingEnforcer")
class ConstrainedDecodingEnforcerTest {

    private static Anchor anchor(String id, String text, Authority authority) {
        return new Anchor(id, text, 500, authority, false, 0.9, 0, null, 0.0, 1.0, MemoryTier.WARM);
    }

    @Nested
    @DisplayName("NoOpConstrainedDecodingEnforcer")
    class NoOpStub {

        @Test
        @DisplayName("computeConstraintMask returns mask with all tokens allowed")
        void maskIsUnconstrainedAllTrue() {
            var enforcer = new NoOpConstrainedDecodingEnforcer();
            var index = AnchorConstraintIndex.build(List.of());
            int vocabSize = 50257;

            var mask = enforcer.computeConstraintMask(index, vocabSize);

            assertThat(mask.vocabularySize()).isEqualTo(vocabSize);
            assertThat(mask.allowedTokens()).hasSize(vocabSize);
            assertThat(mask.constraintCount()).isEqualTo(0);
            for (boolean allowed : mask.allowedTokens()) {
                assertThat(allowed).isTrue();
            }
        }

        @Test
        @DisplayName("enforce returns compliant result")
        void enforceReturnsCompliant() {
            var enforcer = new NoOpConstrainedDecodingEnforcer();
            var anchor = anchor("a1", "Baron Krell rules", Authority.CANON);
            var policy = ComplianceContext.CompliancePolicy.canonOnly();
            var context = new ComplianceContext("Some response", List.of(anchor), policy);

            var result = enforcer.enforce(context);

            assertThat(result.compliant()).isTrue();
            assertThat(result.suggestedAction()).isEqualTo(ComplianceAction.ACCEPT);
            assertThat(result.violations()).isEmpty();
        }

        @Test
        @DisplayName("stub is assignable as ConstrainedDecodingEnforcer and ComplianceEnforcer")
        void interfaceAssignability() {
            var stub = new NoOpConstrainedDecodingEnforcer();
            assertThat(stub).isInstanceOf(ConstrainedDecodingEnforcer.class);
            assertThat(stub).isInstanceOf(ComplianceEnforcer.class);
        }
    }
}
