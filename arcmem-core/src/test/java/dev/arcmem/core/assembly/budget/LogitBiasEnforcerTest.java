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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("LogitBiasEnforcer")
class LogitBiasEnforcerTest {

    private static MemoryUnit unit(String id, String text, Authority authority) {
        return new MemoryUnit(id, text, 500, authority, false, 0.9, 0, null, 0.0, 1.0, MemoryTier.WARM, null);
    }

    private LogitBiasEnforcer enforcer(boolean modelSupportsLogitBias) {
        var detector = mock(ModelCapabilityDetector.class);
        when(detector.supportsLogitBias(org.mockito.ArgumentMatchers.anyString())).thenReturn(modelSupportsLogitBias);
        return new LogitBiasEnforcer(detector);
    }

    private LogitBiasEnforcer enforcerWithRealDetector() {
        return new LogitBiasEnforcer(new ModelCapabilityDetector());
    }

    @Nested
    @DisplayName("buildBiasMap")
    class BuildBiasMap {

        @Test
        @DisplayName("CANON tokens get bias 100, RELIABLE tokens get bias 50")
        void authorityTieredBias() {
            var canon = unit("c1", "Baron Krell leads the sahuagin", Authority.CANON);
            var reliable = unit("r1", "Captain Nyssa commands the fleet", Authority.RELIABLE);
            var enforcer = enforcerWithRealDetector();

            var index = SemanticUnitConstraintIndex.build(List.of(canon, reliable));
            var biasMap = enforcer.buildBiasMap(index);

            // Baron and Krell from CANON unit
            assertThat(biasMap.tokenBiases()).containsEntry("Baron", LogitBiasMap.CANON_BIAS);
            assertThat(biasMap.tokenBiases()).containsEntry("Krell", LogitBiasMap.CANON_BIAS);
            // Captain and Nyssa from RELIABLE unit
            assertThat(biasMap.tokenBiases()).containsEntry("Captain", LogitBiasMap.RELIABLE_BIAS);
            assertThat(biasMap.tokenBiases()).containsEntry("Nyssa", LogitBiasMap.RELIABLE_BIAS);
        }

        @Test
        @DisplayName("overflow count is non-zero when constraints exceed 300 tokens")
        void overflowHandling() {
            // Build an index that has more than 300 distinct tokens
            var units = new java.util.ArrayList<MemoryUnit>();
            for (int i = 0; i < 100; i++) {
                // Each unit has 4 entity names
                var text = "Alpha%d Beta%d Gamma%d Delta%d leads the attack".formatted(i, i, i, i);
                units.add(unit("a" + i, text, Authority.CANON));
            }
            var enforcer = enforcerWithRealDetector();
            var index = SemanticUnitConstraintIndex.build(units);
            var biasMap = enforcer.buildBiasMap(index);

            assertThat(biasMap.tokenBiases().size()).isLessThanOrEqualTo(LogitBiasMap.MAX_TOKENS);
            assertThat(biasMap.overflowCount()).isGreaterThan(0);
        }

        @Test
        @DisplayName("empty memory unit list produces empty bias map")
        void emptyUnitsProducesEmptyMap() {
            var enforcer = enforcerWithRealDetector();
            var index = SemanticUnitConstraintIndex.build(List.of());
            var biasMap = enforcer.buildBiasMap(index);

            assertThat(biasMap.tokenBiases()).isEmpty();
            assertThat(biasMap.constraintCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("buildBiasMapForModel")
    class BuildBiasMapForModel {

        @Test
        @DisplayName("returns empty map when model does not support logit bias")
        void unsupportedModelReturnsEmptyMap() {
            var enforcer = enforcer(false);
            var unit = unit("a1", "Baron Krell is the leader", Authority.CANON);
            var result = enforcer.buildBiasMapForModel(List.of(unit), "claude-3-sonnet");

            assertThat(result.tokenBiases()).isEmpty();
            assertThat(result.constraintCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("gpt-4 model supports logit bias and produces non-empty map")
        void gpt4ModelProducesBiasMap() {
            var enforcer = enforcer(true);
            var unit = unit("a1", "Baron Krell leads the sahuagin", Authority.CANON);
            var result = enforcer.buildBiasMapForModel(List.of(unit), "gpt-4o");

            assertThat(result.tokenBiases()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("enforce")
    class Enforce {

        @Test
        @DisplayName("returns compliant result with valid duration")
        void returnsCompliantResult() {
            var enforcer = enforcerWithRealDetector();
            var unit = unit("a1", "Baron Krell rules", Authority.CANON);
            var policy = ComplianceContext.CompliancePolicy.canonOnly();
            var context = new ComplianceContext("Baron Krell rules the sea", List.of(unit), policy);

            var result = enforcer.enforce(context);

            assertThat(result.compliant()).isTrue();
            assertThat(result.suggestedAction()).isEqualTo(ComplianceAction.ACCEPT);
            assertThat(result.validationDuration()).isNotNull();
        }
    }
}
