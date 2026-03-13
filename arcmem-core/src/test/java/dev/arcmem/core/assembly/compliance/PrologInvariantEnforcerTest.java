package dev.arcmem.core.assembly.compliance;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("PrologInvariantEnforcer")
@ExtendWith(MockitoExtension.class)
class PrologInvariantEnforcerTest {

    private final MemoryUnitPrologProjector realProjector = new MemoryUnitPrologProjector();

    @Mock
    private MemoryUnitPrologProjector mockProjector;

    private MemoryUnit unit(String id, String text, Authority authority) {
        return MemoryUnit.withoutTrust(id, text, 500, authority, false, 0.9, 0);
    }

    private ComplianceContext context(String response, List<MemoryUnit> units,
                                      ComplianceContext.CompliancePolicy policy) {
        return new ComplianceContext(response, units, policy);
    }

    @Nested
    @DisplayName("enforce")
    class Enforce {

        @Test
        @DisplayName("detects contradiction with CANON memory unit and returns REJECT")
        void detectsCanonContradictionAndRejects() {
            var enforcer = new PrologInvariantEnforcer(realProjector);
            var units = List.of(unit("anc-001", "The guardian is alive", Authority.CANON));
            var ctx = context("The guardian is dead", units,
                    ComplianceContext.CompliancePolicy.canonOnly());

            var result = enforcer.enforce(ctx);

            assertThat(result.compliant()).isFalse();
            assertThat(result.suggestedAction()).isEqualTo(ComplianceAction.REJECT);
            assertThat(result.violations()).hasSize(1);
            assertThat(result.violations().getFirst().unitId()).isEqualTo("anc-001");
        }

        @Test
        @DisplayName("returns compliant ACCEPT for non-contradicting response")
        void returnsCompliantForNonContradicting() {
            var enforcer = new PrologInvariantEnforcer(realProjector);
            var units = List.of(unit("anc-001", "The tavern serves ale", Authority.RELIABLE));
            var ctx = context("The king sits on his throne", units,
                    ComplianceContext.CompliancePolicy.tiered());

            var result = enforcer.enforce(ctx);

            assertThat(result.compliant()).isTrue();
            assertThat(result.suggestedAction()).isEqualTo(ComplianceAction.ACCEPT);
        }

        @Test
        @DisplayName("respects CompliancePolicy filter -- canonOnly excludes PROVISIONAL")
        void respectsPolicyFilter() {
            var enforcer = new PrologInvariantEnforcer(realProjector);
            var units = List.of(unit("anc-001", "The guardian is alive", Authority.PROVISIONAL));
            var ctx = context("The guardian is dead", units,
                    ComplianceContext.CompliancePolicy.canonOnly());

            var result = enforcer.enforce(ctx);

            assertThat(result.compliant()).isTrue();
            assertThat(result.suggestedAction()).isEqualTo(ComplianceAction.ACCEPT);
        }

        @Test
        @DisplayName("returns compliant on projector failure (fail-open)")
        void returnsCompliantOnFailure() {
            when(mockProjector.projectWithIncoming(anyList(), anyString()))
                    .thenThrow(new RuntimeException("Prolog engine error"));
            var enforcer = new PrologInvariantEnforcer(mockProjector);
            var units = List.of(unit("anc-001", "The guardian is alive", Authority.CANON));
            var ctx = context("The guardian is dead", units,
                    ComplianceContext.CompliancePolicy.canonOnly());

            var result = enforcer.enforce(ctx);

            assertThat(result.compliant()).isTrue();
            assertThat(result.suggestedAction()).isEqualTo(ComplianceAction.ACCEPT);
        }
    }
}
