package dev.dunnam.diceanchors.anchor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Anchor")
class AnchorTest {

    @Nested
    @DisplayName("clampRank")
    class ClampRank {

        @Test
        @DisplayName("clamps to minimum when below 100")
        void clampRank_belowMinimum_clampsTo100() {
            assertThat(Anchor.clampRank(0)).isEqualTo(Anchor.MIN_RANK);
            assertThat(Anchor.clampRank(-50)).isEqualTo(Anchor.MIN_RANK);
            assertThat(Anchor.clampRank(99)).isEqualTo(Anchor.MIN_RANK);
        }

        @Test
        @DisplayName("clamps to maximum when above 900")
        void clampRank_aboveMaximum_clampsTo900() {
            assertThat(Anchor.clampRank(901)).isEqualTo(Anchor.MAX_RANK);
            assertThat(Anchor.clampRank(1000)).isEqualTo(Anchor.MAX_RANK);
            assertThat(Anchor.clampRank(Integer.MAX_VALUE)).isEqualTo(Anchor.MAX_RANK);
        }

        @Test
        @DisplayName("returns unchanged rank when within [100, 900]")
        void clampRank_withinRange_returnsUnchanged() {
            assertThat(Anchor.clampRank(100)).isEqualTo(100);
            assertThat(Anchor.clampRank(500)).isEqualTo(500);
            assertThat(Anchor.clampRank(900)).isEqualTo(900);
            assertThat(Anchor.clampRank(350)).isEqualTo(350);
        }
    }
}
