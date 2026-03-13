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

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HybridComplianceEnforcer")
class HybridComplianceEnforcerTest {

    private static MemoryUnit unit(String id, String text, Authority authority) {
        return new MemoryUnit(id, text, 500, authority, false, 0.9, 0, null, 0.0, 1.0, MemoryTier.WARM);
    }

    private static ComplianceContext context() {
        return new ComplianceContext(
                "Some DM response",
                List.of(unit("a1", "Baron Krell leads", Authority.CANON)),
                ComplianceContext.CompliancePolicy.canonOnly());
    }

    private static ComplianceEnforcer compliantLayer() {
        return ctx -> ComplianceResult.compliant(Duration.ofMillis(10));
    }

    private static ComplianceEnforcer violatingLayer(ComplianceAction action) {
        return ctx -> new ComplianceResult(
                false,
                List.of(new ComplianceViolation("a1", "text", Authority.CANON, "violation", 0.9)),
                action,
                Duration.ofMillis(5));
    }

    @Nested
    @DisplayName("enforce")
    class Enforce {

        @Test
        @DisplayName("all compliant layers produce compliant aggregate result")
        void allCompliantLayersProduceCompliantResult() {
            var enforcer = new HybridComplianceEnforcer(List.of(compliantLayer(), compliantLayer()));
            var result = enforcer.enforce(context());

            assertThat(result.compliant()).isTrue();
            assertThat(result.violations()).isEmpty();
            assertThat(result.suggestedAction()).isEqualTo(ComplianceAction.ACCEPT);
        }

        @Test
        @DisplayName("single violating layer makes aggregate non-compliant")
        void singleViolatingLayerMakesNonCompliant() {
            var enforcer = new HybridComplianceEnforcer(
                    List.of(compliantLayer(), violatingLayer(ComplianceAction.RETRY)));
            var result = enforcer.enforce(context());

            assertThat(result.compliant()).isFalse();
            assertThat(result.violations()).hasSize(1);
            assertThat(result.suggestedAction()).isEqualTo(ComplianceAction.RETRY);
        }

        @Test
        @DisplayName("REJECT action from any layer dominates result")
        void rejectDominatesOverRetry() {
            var enforcer = new HybridComplianceEnforcer(List.of(
                    violatingLayer(ComplianceAction.RETRY),
                    violatingLayer(ComplianceAction.REJECT)));
            var result = enforcer.enforce(context());

            assertThat(result.suggestedAction()).isEqualTo(ComplianceAction.REJECT);
        }

        @Test
        @DisplayName("violations are union of all layer violations")
        void violationsAreUnion() {
            var enforcer = new HybridComplianceEnforcer(List.of(
                    violatingLayer(ComplianceAction.RETRY),
                    violatingLayer(ComplianceAction.RETRY)));
            var result = enforcer.enforce(context());

            assertThat(result.violations()).hasSize(2);
        }

        @Test
        @DisplayName("validationDuration is sum of all layer durations")
        void durationIsSum() {
            var enforcer = new HybridComplianceEnforcer(List.of(compliantLayer(), compliantLayer()));
            var result = enforcer.enforce(context());

            assertThat(result.validationDuration().toMillis()).isGreaterThanOrEqualTo(20);
        }

        @Test
        @DisplayName("graceful degradation when a layer throws")
        void gracefulDegradationOnLayerException() {
            ComplianceEnforcer throwingLayer = ctx -> { throw new RuntimeException("layer failure"); };
            var enforcer = new HybridComplianceEnforcer(List.of(throwingLayer, compliantLayer()));

            var result = enforcer.enforce(context());
            // Should not throw; remaining compliant layer runs
            assertThat(result.compliant()).isTrue();
        }

        @Test
        @DisplayName("empty layer list produces compliant result with ACCEPT")
        void emptyLayersProducesCompliant() {
            var enforcer = new HybridComplianceEnforcer(List.of());
            var result = enforcer.enforce(context());

            assertThat(result.compliant()).isTrue();
            assertThat(result.suggestedAction()).isEqualTo(ComplianceAction.ACCEPT);
        }
    }
}
