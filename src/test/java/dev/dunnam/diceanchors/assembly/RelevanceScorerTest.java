package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.DiceAnchorsProperties.ScoringConfig;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.MemoryTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("RelevanceScorer")
class RelevanceScorerTest {

    private final RelevanceScorer scorer = new RelevanceScorer();
    private final ScoringConfig defaultScoring = new ScoringConfig(0.4, 0.3, 0.3);

    @Nested
    @DisplayName("Heuristic Scoring")
    class HeuristicScoring {

        @Test
        @DisplayName("CANON/HOT/0.9 scores highest at 0.97")
        void canonHotHighConfidenceScoresHighest() {
            var anchor = new Anchor("c1", "Canon fact", 800, Authority.CANON, true, 0.9, 10,
                    null, 0.0, 1.0, MemoryTier.HOT);

            var score = scorer.computeHeuristicScore(anchor, defaultScoring);

            // (0.4 * 1.0) + (0.3 * 1.0) + (0.3 * 0.9) = 0.4 + 0.3 + 0.27 = 0.97
            assertThat(score).isCloseTo(0.97, within(0.001));
        }

        @Test
        @DisplayName("PROVISIONAL/COLD/0.3 scores lowest at 0.33")
        void provisionalColdLowConfidenceScoresLowest() {
            var anchor = new Anchor("p1", "Provisional fact", 200, Authority.PROVISIONAL, false, 0.3, 0,
                    null, 0.0, 1.0, MemoryTier.COLD);

            var score = scorer.computeHeuristicScore(anchor, defaultScoring);

            // (0.4 * 0.3) + (0.3 * 0.4) + (0.3 * 0.3) = 0.12 + 0.12 + 0.09 = 0.33
            assertThat(score).isCloseTo(0.33, within(0.001));
        }

        @Test
        @DisplayName("RELIABLE/WARM/0.7 scores in the middle at 0.74")
        void reliableWarmMediumConfidence() {
            var anchor = new Anchor("r1", "Reliable fact", 600, Authority.RELIABLE, false, 0.7, 5,
                    null, 0.0, 1.0, MemoryTier.WARM);

            var score = scorer.computeHeuristicScore(anchor, defaultScoring);

            // (0.4 * 0.8) + (0.3 * 0.7) + (0.3 * 0.7) = 0.32 + 0.21 + 0.21 = 0.74
            assertThat(score).isCloseTo(0.74, within(0.001));
        }

        @Test
        @DisplayName("scoreAndRank returns anchors sorted by descending relevance score")
        void scoreAndRankReturnsSortedDescending() {
            var canonHot = new Anchor("c1", "Canon fact", 800, Authority.CANON, true, 0.9, 10,
                    null, 0.0, 1.0, MemoryTier.HOT);
            var reliableWarm = new Anchor("r1", "Reliable fact", 600, Authority.RELIABLE, false, 0.7, 5,
                    null, 0.0, 1.0, MemoryTier.WARM);
            var provisionalCold = new Anchor("p1", "Provisional fact", 200, Authority.PROVISIONAL, false, 0.3, 0,
                    null, 0.0, 1.0, MemoryTier.COLD);

            var scored = scorer.scoreAndRank(List.of(provisionalCold, canonHot, reliableWarm), defaultScoring);

            assertThat(scored).hasSize(3);
            assertThat(scored.get(0).id()).isEqualTo("c1");
            assertThat(scored.get(1).id()).isEqualTo("r1");
            assertThat(scored.get(2).id()).isEqualTo("p1");

            // Verify descending order
            assertThat(scored.get(0).relevanceScore()).isGreaterThan(scored.get(1).relevanceScore());
            assertThat(scored.get(1).relevanceScore()).isGreaterThan(scored.get(2).relevanceScore());
        }
    }
}
