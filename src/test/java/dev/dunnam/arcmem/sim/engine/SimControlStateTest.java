package dev.dunnam.diceanchors.sim.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimControlState")
class SimControlStateTest {

    @Test
    @DisplayName("allows rerun transition from COMPLETED to RUNNING")
    void allowsRerunFromCompletedToRunning() {
        assertThat(SimControlState.COMPLETED.canTransitionTo(SimControlState.RUNNING)).isTrue();
    }

    @Test
    @DisplayName("keeps invalid transition from IDLE to COMPLETED blocked")
    void keepsIdleToCompletedBlocked() {
        assertThat(SimControlState.IDLE.canTransitionTo(SimControlState.COMPLETED)).isFalse();
    }
}
