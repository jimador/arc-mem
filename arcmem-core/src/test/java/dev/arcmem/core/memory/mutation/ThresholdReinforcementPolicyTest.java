package dev.arcmem.core.memory.mutation;
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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ThresholdReinforcementPolicy")
class ThresholdReinforcementPolicyTest {

    private final ReinforcementPolicy policy = ReinforcementPolicy.threshold();

    @Test
    @DisplayName("rank boost is constant")
    void rankBoostIsConstant() {
        var unit = MemoryUnit.withoutTrust("1", "test", 500, Authority.PROVISIONAL, false, 0.9, 0);
        assertThat(policy.calculateRankBoost(unit)).isEqualTo(50);
    }

    @Test
    @DisplayName("should upgrade PROVISIONAL to UNRELIABLE at threshold")
    void upgradeProvisionalAtThreshold() {
        var unit = MemoryUnit.withoutTrust("1", "test", 500, Authority.PROVISIONAL, false, 0.9, 3);
        assertThat(policy.shouldUpgradeAuthority(unit)).isTrue();
    }

    @Test
    @DisplayName("should not upgrade PROVISIONAL below threshold")
    void noUpgradeProvisionalBelowThreshold() {
        var unit = MemoryUnit.withoutTrust("1", "test", 500, Authority.PROVISIONAL, false, 0.9, 2);
        assertThat(policy.shouldUpgradeAuthority(unit)).isFalse();
    }

    @Test
    @DisplayName("should upgrade UNRELIABLE to RELIABLE at threshold")
    void upgradeUnreliableAtThreshold() {
        var unit = MemoryUnit.withoutTrust("1", "test", 600, Authority.UNRELIABLE, false, 0.9, 7);
        assertThat(policy.shouldUpgradeAuthority(unit)).isTrue();
    }

    @Test
    @DisplayName("should never upgrade CANON")
    void neverUpgradeCanon() {
        var unit = MemoryUnit.withoutTrust("1", "test", 850, Authority.CANON, true, 0.99, 100);
        assertThat(policy.shouldUpgradeAuthority(unit)).isFalse();
    }
}
