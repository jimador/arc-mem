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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimulationProgress")
class SimulationProgressTest {

    @Nested
    @DisplayName("turnDurationMs field")
    class TurnDurationMs {

        @Test
        @DisplayName("preserved for ATTACK turn phase")
        void attackTurnPreservesDuration() {
            var progress = attackProgress(1500L);
            assertThat(progress.turnDurationMs()).isEqualTo(1500L);
            assertThat(progress.turnDurationMs()).isGreaterThan(0L);
        }

        @Test
        @DisplayName("preserved for ESTABLISH turn phase")
        void establishTurnPreservesDuration() {
            var progress = new SimulationProgress(
                    SimulationProgress.SimulationPhase.ESTABLISH, TurnType.ESTABLISH, null,
                    2, 10, "player", "dm", List.of(), null, List.of(),
                    false, "status", true, null, null, List.of(), null, 2300L, null, null, null);
            assertThat(progress.turnDurationMs()).isGreaterThan(0L);
        }

        @Test
        @DisplayName("zero for COMPLETE phase")
        void completePhaseDurationIsZero() {
            var progress = new SimulationProgress(
                    SimulationProgress.SimulationPhase.COMPLETE, null, null,
                    10, 10, null, null, List.of(), null, List.of(),
                    true, "done", false, null, null, List.of(), null, 0L, null, null, null);
            assertThat(progress.turnDurationMs()).isEqualTo(0L);
        }

        @Test
        @DisplayName("zero for SETUP phase")
        void setupPhaseDurationIsZero() {
            var progress = new SimulationProgress(
                    SimulationProgress.SimulationPhase.SETUP, null, null,
                    0, 10, null, null, List.of(), null, List.of(),
                    false, "seeding", true, null, null, List.of(), null, 0L, null, null, null);
            assertThat(progress.turnDurationMs()).isEqualTo(0L);
        }

        @Test
        @DisplayName("zero for pre-turn thinking event (lastDmResponse null)")
        void preTurnEventDurationIsZero() {
            var progress = new SimulationProgress(
                    SimulationProgress.SimulationPhase.ATTACK, TurnType.ATTACK, null,
                    3, 10, "player msg", null, List.of(), null, List.of(),
                    false, "thinking...", true, null, null, List.of(), null, 0L, null, null, null);
            assertThat(progress.lastDmResponse()).isNull();
            assertThat(progress.turnDurationMs()).isEqualTo(0L);
        }
    }

    private SimulationProgress attackProgress(long durationMs) {
        return new SimulationProgress(
                SimulationProgress.SimulationPhase.ATTACK, TurnType.ATTACK, null,
                1, 10, "player msg", "dm response", List.of(), null, List.of(),
                false, "T1/10 — ATTACK", true, null, null, List.of(), null, durationMs, null, null, null);
    }
}
