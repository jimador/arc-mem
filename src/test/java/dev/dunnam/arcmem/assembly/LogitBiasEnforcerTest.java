package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.MemoryTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("LogitBiasEnforcer")
class LogitBiasEnforcerTest {

    private static Anchor anchor(String id, String text, Authority authority) {
        return new Anchor(id, text, 500, authority, false, 0.9, 0, null, 0.0, 1.0, MemoryTier.WARM);
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
            var canon = anchor("c1", "Baron Krell leads the sahuagin", Authority.CANON);
            var reliable = anchor("r1", "Captain Nyssa commands the fleet", Authority.RELIABLE);
            var enforcer = enforcerWithRealDetector();

            var index = AnchorConstraintIndex.build(List.of(canon, reliable));
            var biasMap = enforcer.buildBiasMap(index);

            // Baron and Krell from CANON anchor
            assertThat(biasMap.tokenBiases()).containsEntry("Baron", LogitBiasMap.CANON_BIAS);
            assertThat(biasMap.tokenBiases()).containsEntry("Krell", LogitBiasMap.CANON_BIAS);
            // Captain and Nyssa from RELIABLE anchor
            assertThat(biasMap.tokenBiases()).containsEntry("Captain", LogitBiasMap.RELIABLE_BIAS);
            assertThat(biasMap.tokenBiases()).containsEntry("Nyssa", LogitBiasMap.RELIABLE_BIAS);
        }

        @Test
        @DisplayName("overflow count is non-zero when constraints exceed 300 tokens")
        void overflowHandling() {
            // Build an index that has more than 300 distinct tokens
            var anchors = new java.util.ArrayList<Anchor>();
            for (int i = 0; i < 100; i++) {
                // Each anchor has 4 entity names
                var text = "Alpha%d Beta%d Gamma%d Delta%d leads the attack".formatted(i, i, i, i);
                anchors.add(anchor("a" + i, text, Authority.CANON));
            }
            var enforcer = enforcerWithRealDetector();
            var index = AnchorConstraintIndex.build(anchors);
            var biasMap = enforcer.buildBiasMap(index);

            assertThat(biasMap.tokenBiases().size()).isLessThanOrEqualTo(LogitBiasMap.MAX_TOKENS);
            assertThat(biasMap.overflowCount()).isGreaterThan(0);
        }

        @Test
        @DisplayName("empty anchor list produces empty bias map")
        void emptyAnchorsProducesEmptyMap() {
            var enforcer = enforcerWithRealDetector();
            var index = AnchorConstraintIndex.build(List.of());
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
            var anchor = anchor("a1", "Baron Krell is the leader", Authority.CANON);
            var result = enforcer.buildBiasMapForModel(List.of(anchor), "claude-3-sonnet");

            assertThat(result.tokenBiases()).isEmpty();
            assertThat(result.constraintCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("gpt-4 model supports logit bias and produces non-empty map")
        void gpt4ModelProducesBiasMap() {
            var enforcer = enforcer(true);
            var anchor = anchor("a1", "Baron Krell leads the sahuagin", Authority.CANON);
            var result = enforcer.buildBiasMapForModel(List.of(anchor), "gpt-4o");

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
            var anchor = anchor("a1", "Baron Krell rules", Authority.CANON);
            var policy = ComplianceContext.CompliancePolicy.canonOnly();
            var context = new ComplianceContext("Baron Krell rules the sea", List.of(anchor), policy);

            var result = enforcer.enforce(context);

            assertThat(result.compliant()).isTrue();
            assertThat(result.suggestedAction()).isEqualTo(ComplianceAction.ACCEPT);
            assertThat(result.validationDuration()).isNotNull();
        }
    }
}
