package dev.dunnam.diceanchors.anchor;

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
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@DisplayName("Trust Signals")
@ExtendWith(MockitoExtension.class)
class TrustSignalTest {

    private static final String CONTEXT_ID = "test-ctx";

    @Nested
    @DisplayName("GraphConsistencySignal")
    class GraphConsistencySignalTests {

        @Mock
        private AnchorEngine anchorEngine;

        private GraphConsistencySignal signal;

        @BeforeEach
        void setUp() {
            signal = new GraphConsistencySignal(anchorEngine);
        }

        @Test
        @DisplayName("cold-start returns 0.5 when no anchors exist")
        void coldStartReturnsNeutralScore() {
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(List.of());
            var proposition = new PropositionNode("The dragon is hostile", 0.8);

            var result = signal.evaluate(proposition, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("Jaccard similarity computed correctly with known token sets")
        void jaccardComputedCorrectlyWithKnownTokens() {
            // Proposition: "dragon attacks village" -> tokens: {dragon, attacks, village}
            // Anchor: "dragon destroys village" -> tokens: {dragon, destroys, village}
            // Intersection: {dragon, village} = 2
            // Union: {dragon, attacks, village, destroys} = 4
            // Jaccard: 2/4 = 0.5
            var anchor = Anchor.withoutTrust(
                    "a-1", "dragon destroys village", 500, Authority.RELIABLE, false, 0.9, 2);
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(List.of(anchor));

            var proposition = new PropositionNode("dragon attacks village", 0.8);
            proposition.setId("p-1"); // different from anchor to avoid self-comparison

            var result = signal.evaluate(proposition, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isCloseTo(0.5, within(0.01));
        }

        @Test
        @DisplayName("stop words are excluded from Jaccard computation")
        void stopWordsExcludedFromJaccard() {
            // Proposition: "the dragon is in the cave" -> tokens after stop removal: {dragon, cave}
            // Anchor: "a dragon was in a cave" -> tokens after stop removal: {dragon, cave}
            // Jaccard: 2/2 = 1.0
            var anchor = Anchor.withoutTrust(
                    "a-1", "a dragon was in a cave", 500, Authority.RELIABLE, false, 0.9, 2);
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(List.of(anchor));

            var proposition = new PropositionNode("the dragon is in the cave", 0.8);
            proposition.setId("p-1");

            var result = signal.evaluate(proposition, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isCloseTo(1.0, within(0.01));
        }

        @Test
        @DisplayName("skips self-comparison and returns 0.0 when only self matches")
        void skipsSelfComparisonReturnsZero() {
            var anchor = Anchor.withoutTrust(
                    "same-id", "exact same text", 500, Authority.RELIABLE, false, 0.9, 2);
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(List.of(anchor));

            var proposition = new PropositionNode("exact same text", 0.8);
            proposition.setId("same-id");

            var result = signal.evaluate(proposition, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns max Jaccard similarity across multiple anchors")
        void returnsMaxSimilarityAcrossAnchors() {
            var lowMatch = Anchor.withoutTrust(
                    "a-1", "elves trade gems", 500, Authority.PROVISIONAL, false, 0.7, 0);
            var highMatch = Anchor.withoutTrust(
                    "a-2", "dragon attacks tower", 600, Authority.RELIABLE, false, 0.9, 2);
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(List.of(lowMatch, highMatch));

            // "dragon attacks castle" shares 2/4 words with highMatch, 0/4 with lowMatch
            var proposition = new PropositionNode("dragon attacks castle", 0.8);
            proposition.setId("p-1");

            var result = signal.evaluate(proposition, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isGreaterThan(0.0);
        }
    }

    @Nested
    @DisplayName("CorroborationSignal")
    class CorroborationSignalTests {

        private TrustSignal signal;

        @BeforeEach
        void setUp() {
            signal = TrustSignal.corroboration();
        }

        @Test
        @DisplayName("returns empty for null sourceIds")
        void nullSourceIdsReturnsEmpty() {
            var proposition = new PropositionNode("Some fact", 0.8);
            proposition.setSourceIds(null);

            var result = signal.evaluate(proposition, CONTEXT_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty for empty sourceIds")
        void emptySourceIdsReturnsEmpty() {
            var proposition = new PropositionNode("Some fact", 0.8);
            proposition.setSourceIds(List.of());

            var result = signal.evaluate(proposition, CONTEXT_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("single source returns 0.3")
        void singleSourceReturnsPointThree() {
            var proposition = new PropositionNode("Some fact", 0.8);
            proposition.setSourceIds(List.of("source-1"));

            var result = signal.evaluate(proposition, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isEqualTo(0.3);
        }

        @Test
        @DisplayName("two distinct sources returns 0.6")
        void twoSourcesReturnsPointSix() {
            var proposition = new PropositionNode("Some fact", 0.8);
            proposition.setSourceIds(List.of("source-1", "source-2"));

            var result = signal.evaluate(proposition, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isEqualTo(0.6);
        }

        @Test
        @DisplayName("three or more distinct sources returns 0.9")
        void threeOrMoreSourcesReturnsPointNine() {
            var proposition = new PropositionNode("Some fact", 0.8);
            proposition.setSourceIds(List.of("source-1", "source-2", "source-3"));

            var result = signal.evaluate(proposition, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isEqualTo(0.9);
        }

        @Test
        @DisplayName("duplicate sourceIds are counted once")
        void duplicateSourceIdsCountedOnce() {
            var proposition = new PropositionNode("Some fact", 0.8);
            proposition.setSourceIds(List.of("source-1", "source-1", "source-1"));

            var result = signal.evaluate(proposition, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isEqualTo(0.3);
        }
    }
}
