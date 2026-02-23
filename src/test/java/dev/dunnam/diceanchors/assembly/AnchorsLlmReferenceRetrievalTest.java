package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.DiceAnchorsProperties.RetrievalConfig;
import dev.dunnam.diceanchors.DiceAnchorsProperties.ScoringConfig;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.CompliancePolicy;
import dev.dunnam.diceanchors.anchor.MemoryTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("AnchorsLlmReference retrieval modes")
@ExtendWith(MockitoExtension.class)
class AnchorsLlmReferenceRetrievalTest {

    @Mock
    private AnchorEngine engine;

    private final RelevanceScorer scorer = new RelevanceScorer();
    private final ScoringConfig defaultScoring = new ScoringConfig(0.4, 0.3, 0.3);

    private static List<Anchor> mixedAnchors() {
        var anchors = new ArrayList<Anchor>();
        // 2 CANON anchors
        anchors.add(new Anchor("canon1", "Canon fact one", 800, Authority.CANON, true, 0.9, 10,
                null, 0.0, 1.0, MemoryTier.HOT));
        anchors.add(new Anchor("canon2", "Canon fact two", 850, Authority.CANON, true, 0.95, 12,
                null, 0.0, 1.0, MemoryTier.HOT));
        // 13 non-CANON anchors with varying authority/tier/confidence
        anchors.add(new Anchor("rel1", "Reliable hot fact", 700, Authority.RELIABLE, false, 0.85, 7,
                null, 0.0, 1.0, MemoryTier.HOT));
        anchors.add(new Anchor("rel2", "Reliable warm fact", 600, Authority.RELIABLE, false, 0.75, 5,
                null, 0.0, 1.0, MemoryTier.WARM));
        anchors.add(new Anchor("rel3", "Reliable cold fact", 400, Authority.RELIABLE, false, 0.65, 3,
                null, 0.0, 1.0, MemoryTier.COLD));
        anchors.add(new Anchor("unr1", "Unreliable hot fact", 500, Authority.UNRELIABLE, false, 0.7, 2,
                null, 0.0, 1.0, MemoryTier.HOT));
        anchors.add(new Anchor("unr2", "Unreliable warm fact", 400, Authority.UNRELIABLE, false, 0.6, 1,
                null, 0.0, 1.0, MemoryTier.WARM));
        anchors.add(new Anchor("unr3", "Unreliable cold fact", 300, Authority.UNRELIABLE, false, 0.5, 0,
                null, 0.0, 1.0, MemoryTier.COLD));
        anchors.add(new Anchor("prov1", "Provisional hot fact", 400, Authority.PROVISIONAL, false, 0.6, 0,
                null, 0.0, 1.0, MemoryTier.HOT));
        anchors.add(new Anchor("prov2", "Provisional warm fact", 350, Authority.PROVISIONAL, false, 0.5, 0,
                null, 0.0, 1.0, MemoryTier.WARM));
        anchors.add(new Anchor("prov3", "Provisional cold fact", 200, Authority.PROVISIONAL, false, 0.4, 0,
                null, 0.0, 1.0, MemoryTier.COLD));
        anchors.add(new Anchor("prov4", "Provisional cold low", 150, Authority.PROVISIONAL, false, 0.3, 0,
                null, 0.0, 1.0, MemoryTier.COLD));
        anchors.add(new Anchor("prov5", "Provisional cold lowest", 120, Authority.PROVISIONAL, false, 0.25, 0,
                null, 0.0, 1.0, MemoryTier.COLD));
        anchors.add(new Anchor("prov6", "Provisional cold extra", 110, Authority.PROVISIONAL, false, 0.2, 0,
                null, 0.0, 1.0, MemoryTier.COLD));
        return anchors;
    }

    @Nested
    @DisplayName("Bulk mode")
    class BulkMode {

        @Test
        @DisplayName("BULK mode returns all anchors within budget")
        void bulkModeReturnsAllAnchors() {
            var anchors = mixedAnchors();
            when(engine.inject(anyString())).thenReturn(anchors);

            var config = new RetrievalConfig(RetrievalMode.BULK, 0.0, 5, 5, defaultScoring);
            var ref = new AnchorsLlmReference(engine, "test-ctx", 20,
                    CompliancePolicy.tiered(), 0, null, null, config, scorer);

            var result = ref.getAnchors();

            assertThat(result).hasSize(anchors.size());
        }

        @Test
        @DisplayName("null config behaves as BULK")
        void nullConfigBehavesAsBulk() {
            var anchors = mixedAnchors();
            when(engine.inject(anyString())).thenReturn(anchors);

            var refNull = new AnchorsLlmReference(engine, "test-ctx", 20,
                    CompliancePolicy.tiered(), 0, null, null, null, scorer);
            var refBulk = new AnchorsLlmReference(engine, "test-ctx2", 20,
                    CompliancePolicy.tiered(), 0, null, null,
                    new RetrievalConfig(RetrievalMode.BULK, 0.0, 5, 5, defaultScoring), scorer);

            var resultNull = refNull.getAnchors();
            var resultBulk = refBulk.getAnchors();

            assertThat(resultNull).hasSameSizeAs(resultBulk);
        }
    }

    @Nested
    @DisplayName("Hybrid mode")
    class HybridMode {

        @Test
        @DisplayName("hybrid mode with baselineTopK=5 reduces non-CANON anchors to 5")
        void hybridModeReducesBaseline() {
            var anchors = mixedAnchors(); // 2 CANON + 13 non-CANON = 15
            when(engine.inject(anyString())).thenReturn(anchors);

            var config = new RetrievalConfig(RetrievalMode.HYBRID, 0.0, 5, 5, defaultScoring);
            var ref = new AnchorsLlmReference(engine, "test-ctx", 20,
                    CompliancePolicy.tiered(), 0, null, null, config, scorer);

            var result = ref.getAnchors();

            // 2 CANON always included + 5 top-k non-CANON = 7
            assertThat(result).hasSize(7);
        }

        @Test
        @DisplayName("CANON anchors always included in hybrid mode")
        void canonAlwaysIncludedInHybrid() {
            var anchors = mixedAnchors();
            when(engine.inject(anyString())).thenReturn(anchors);

            var config = new RetrievalConfig(RetrievalMode.HYBRID, 0.0, 3, 5, defaultScoring);
            var ref = new AnchorsLlmReference(engine, "test-ctx", 20,
                    CompliancePolicy.tiered(), 0, null, null, config, scorer);

            var result = ref.getAnchors();

            var canonIds = result.stream()
                    .filter(a -> a.authority() == Authority.CANON)
                    .map(Anchor::id)
                    .toList();
            assertThat(canonIds).containsExactlyInAnyOrder("canon1", "canon2");
        }

        @Test
        @DisplayName("quality gate filters low relevance anchors")
        void qualityGateFiltersLowRelevance() {
            var anchors = mixedAnchors();
            when(engine.inject(anyString())).thenReturn(anchors);

            // Set minRelevance high enough to filter out PROVISIONAL/COLD anchors
            var config = new RetrievalConfig(RetrievalMode.HYBRID, 0.5, 15, 5, defaultScoring);
            var ref = new AnchorsLlmReference(engine, "test-ctx", 20,
                    CompliancePolicy.tiered(), 0, null, null, config, scorer);

            var result = ref.getAnchors();

            // All included non-CANON anchors should have heuristic score >= 0.5
            var nonCanonAnchors = result.stream()
                    .filter(a -> a.authority() != Authority.CANON)
                    .toList();
            for (var anchor : nonCanonAnchors) {
                var score = scorer.computeHeuristicScore(anchor, defaultScoring);
                assertThat(score).as("Anchor %s should have score >= 0.5", anchor.id())
                        .isGreaterThanOrEqualTo(0.5);
            }

            // CANON anchors are still present regardless
            assertThat(result.stream().filter(a -> a.authority() == Authority.CANON).count())
                    .isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Tool mode")
    class ToolMode {

        @Test
        @DisplayName("TOOL mode returns empty baseline")
        void toolModeReturnsEmptyBaseline() {
            var config = new RetrievalConfig(RetrievalMode.TOOL, 0.0, 5, 5, defaultScoring);
            var ref = new AnchorsLlmReference(engine, "test-ctx", 20,
                    CompliancePolicy.tiered(), 0, null, null, config, scorer);

            var result = ref.getAnchors();

            assertThat(result).isEmpty();
        }
    }
}
