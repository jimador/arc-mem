package dev.dunnam.diceanchors.anchor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ThresholdReinforcementPolicy")
class ThresholdReinforcementPolicyTest {

    private final ThresholdReinforcementPolicy policy = new ThresholdReinforcementPolicy();

    @Test
    @DisplayName("rank boost is constant")
    void rankBoostIsConstant() {
        var anchor = Anchor.withoutTrust("1", "test", 500, Authority.PROVISIONAL, false, 0.9, 0);
        assertThat(policy.calculateRankBoost(anchor)).isEqualTo(50);
    }

    @Test
    @DisplayName("should upgrade PROVISIONAL to UNRELIABLE at threshold")
    void upgradeProvisionalAtThreshold() {
        var anchor = Anchor.withoutTrust("1", "test", 500, Authority.PROVISIONAL, false, 0.9, 3);
        assertThat(policy.shouldUpgradeAuthority(anchor)).isTrue();
    }

    @Test
    @DisplayName("should not upgrade PROVISIONAL below threshold")
    void noUpgradeProvisionalBelowThreshold() {
        var anchor = Anchor.withoutTrust("1", "test", 500, Authority.PROVISIONAL, false, 0.9, 2);
        assertThat(policy.shouldUpgradeAuthority(anchor)).isFalse();
    }

    @Test
    @DisplayName("should upgrade UNRELIABLE to RELIABLE at threshold")
    void upgradeUnreliableAtThreshold() {
        var anchor = Anchor.withoutTrust("1", "test", 600, Authority.UNRELIABLE, false, 0.9, 7);
        assertThat(policy.shouldUpgradeAuthority(anchor)).isTrue();
    }

    @Test
    @DisplayName("should never upgrade CANON")
    void neverUpgradeCanon() {
        var anchor = Anchor.withoutTrust("1", "test", 850, Authority.CANON, true, 0.99, 100);
        assertThat(policy.shouldUpgradeAuthority(anchor)).isFalse();
    }
}
