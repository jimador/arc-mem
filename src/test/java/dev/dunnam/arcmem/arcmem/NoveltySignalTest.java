package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.persistence.PropositionNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@DisplayName("NoveltySignal")
@ExtendWith(MockitoExtension.class)
class NoveltySignalTest {

    private static final String CONTEXT_ID = "test-ctx";

    @Mock
    private AnchorEngine anchorEngine;

    @Mock
    private DiceAnchorsProperties properties;

    @Mock
    private DiceAnchorsProperties.AnchorConfig anchorConfig;

    @Mock
    private DiceAnchorsProperties.QualityScoringConfig qualityScoringConfig;

    private NoveltySignal signal;

    @BeforeEach
    void setUp() {
        lenient().when(properties.anchor()).thenReturn(anchorConfig);
        lenient().when(anchorConfig.qualityScoring()).thenReturn(qualityScoringConfig);
        lenient().when(qualityScoringConfig.enabled()).thenReturn(true);
        signal = new NoveltySignal(anchorEngine, properties);
    }

    private static Anchor anchor(String id, String text) {
        return Anchor.withoutTrust(id, text, 500, Authority.RELIABLE, false, 0.9, 1);
    }

    private static PropositionNode proposition(String text) {
        return new PropositionNode(text, 0.8);
    }

    @Nested
    @DisplayName("disabled configuration")
    class DisabledConfig {

        @Test
        @DisplayName("returns empty when quality scoring is disabled")
        void disabledReturnsEmpty() {
            when(qualityScoringConfig.enabled()).thenReturn(false);
            var prop = proposition("The dragon guards the gate");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when qualityScoring config is null")
        void nullConfigReturnsEmpty() {
            when(anchorConfig.qualityScoring()).thenReturn(null);
            var prop = proposition("The dragon guards the gate");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("empty anchor context")
    class EmptyContext {

        @Test
        @DisplayName("returns 1.0 when no active anchors exist")
        void noAnchorsReturnsMaxNovelty() {
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(List.of());
            var prop = proposition("The artifact requires a blood sacrifice");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("novelty scoring")
    class NoveltyScoring {

        @Test
        @DisplayName("identical proposition to anchor yields novelty 0.0")
        void identicalPropositionYieldsZeroNovelty() {
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(
                    List.of(anchor("a-1", "The dragon guards the eastern gate")));
            var prop = proposition("the dragon guards the eastern gate");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("completely novel proposition yields novelty 1.0")
        void completelyNovelPropositionYieldsMaxNovelty() {
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(List.of(
                    anchor("a-1", "rain falls heavily"),
                    anchor("a-2", "mountains rise tall")));
            // "sacrifice" and "artifact" don't appear in anchors at all
            var prop = proposition("artifact requires sacrifice");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isGreaterThan(0.7);
        }

        @Test
        @DisplayName("partial overlap yields score between 0.0 and 1.0")
        void partialOverlapYieldsMidRangeScore() {
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(
                    List.of(anchor("a-1", "dragon breathes fire")));
            var prop = proposition("dragon hoards gold");
            // "dragon" overlaps; "hoards", "gold" vs "breathes", "fire" do not

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isGreaterThan(0.0).isLessThan(1.0);
        }

        @Test
        @DisplayName("returns max across multiple anchors (closest match used)")
        void returnsMaxSimilarityAcrossAnchors() {
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(List.of(
                    anchor("a-1", "elves trade gems"),
                    anchor("a-2", "dragon guards eastern gate")));
            var prop = proposition("dragon guards northern gate");
            // Closer to a-2 than a-1

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            // dragon and gate overlap with a-2, so novelty should be relatively low
            assertThat(result.getAsDouble()).isLessThan(0.7);
        }
    }

    @Nested
    @DisplayName("stop word filtering")
    class StopWordFiltering {

        @Test
        @DisplayName("stop words excluded — 'the dragon is in the cave' vs 'a dragon was in a cave' share content words")
        void stopWordsExcludedFromSimilarity() {
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(
                    List.of(anchor("a-1", "a dragon was in a cave")));
            var prop = proposition("the dragon is in the cave");
            // After stop-word removal: {dragon, cave} vs {dragon, cave} -> identical

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            // Jaccard of identical sets = 1.0, so novelty = 0.0
            assertThat(result.getAsDouble()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("proposition of only stop words tokenizes to empty set")
        void stopWordsOnlyPropositionHandledGracefully() {
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(
                    List.of(anchor("a-1", "the guardian is strong")));
            var prop = proposition("the and or is are");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isBetween(0.0, 1.0);
        }
    }

    @Nested
    @DisplayName("name()")
    class NameMethod {

        @Test
        @DisplayName("returns 'novelty'")
        void nameIsNovelty() {
            assertThat(signal.name()).isEqualTo("novelty");
        }
    }
}
