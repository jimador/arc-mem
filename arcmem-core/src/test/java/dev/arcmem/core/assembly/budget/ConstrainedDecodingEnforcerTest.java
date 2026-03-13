package dev.arcmem.core.assembly.budget;
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

@DisplayName("ConstrainedDecodingEnforcer")
class ConstrainedDecodingEnforcerTest {

    private static MemoryUnit unit(String id, String text, Authority authority) {
        return new MemoryUnit(id, text, 500, authority, false, 0.9, 0, null, 0.0, 1.0, MemoryTier.WARM, null);
    }

    @Nested
    @DisplayName("NoOpConstrainedDecodingEnforcer")
    class NoOpStub {

        @Test
        @DisplayName("computeConstraintMask returns mask with all tokens allowed")
        void maskIsUnconstrainedAllTrue() {
            var enforcer = new NoOpConstrainedDecodingEnforcer();
            var index = SemanticUnitConstraintIndex.build(List.of());
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
            var unit = unit("a1", "Baron Krell rules", Authority.CANON);
            var policy = ComplianceContext.CompliancePolicy.canonOnly();
            var context = new ComplianceContext("Some response", List.of(unit), policy);

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
