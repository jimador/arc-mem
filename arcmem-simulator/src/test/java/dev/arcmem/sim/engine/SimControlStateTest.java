package dev.arcmem.simulator.engine;
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

import dev.arcmem.core.spi.llm.*;
import dev.arcmem.simulator.engine.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

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
