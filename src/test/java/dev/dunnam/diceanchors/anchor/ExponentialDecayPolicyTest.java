package dev.dunnam.diceanchors.anchor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExponentialDecayPolicy")
class ExponentialDecayPolicyTest {

    private final ExponentialDecayPolicy policy = new ExponentialDecayPolicy(168.0); // 1 week half-life

    @Test
    @DisplayName("no decay at time zero")
    void noDecayAtZero() {
        var anchor = Anchor.withoutTrust("1", "test", 500, Authority.RELIABLE, false, 0.9, 0);
        assertThat(policy.applyDecay(anchor, 0)).isEqualTo(500);
    }

    @Test
    @DisplayName("halves rank at half-life")
    void halvesAtHalfLife() {
        var anchor = Anchor.withoutTrust("1", "test", 500, Authority.RELIABLE, false, 0.9, 0);
        var decayed = policy.applyDecay(anchor, 168);
        assertThat(decayed).isEqualTo(250); // 500 * 0.5 = 250
    }

    @Test
    @DisplayName("pinned anchors immune to decay")
    void pinnedImmuneToDecay() {
        var anchor = Anchor.withoutTrust("1", "test", 800, Authority.CANON, true, 0.99, 5);
        assertThat(policy.applyDecay(anchor, 1000)).isEqualTo(800);
    }

    @Test
    @DisplayName("decayed rank clamped to minimum")
    void decayedRankClamped() {
        var anchor = Anchor.withoutTrust("1", "test", 200, Authority.PROVISIONAL, false, 0.5, 0);
        var decayed = policy.applyDecay(anchor, 10000); // extreme decay
        assertThat(decayed).isGreaterThanOrEqualTo(Anchor.MIN_RANK);
    }
}
