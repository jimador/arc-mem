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
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

/**
 * Formalizes the simulation lifecycle state machine.
 * <p>
 * Valid transitions:
 * <pre>
 *   IDLE     -> RUNNING
 *   RUNNING  -> PAUSED, COMPLETED
 *   PAUSED   -> RUNNING, COMPLETED
 *   COMPLETED -> IDLE, RUNNING
 * </pre>
 * <p>
 * Side effects per state:
 * <ul>
 *   <li>{@code IDLE} — controls enabled, panels reset</li>
 *   <li>{@code RUNNING} — controls disabled (except Pause/Stop), panels updating</li>
 *   <li>{@code PAUSED} — manipulation panel visible, Pause disabled, Resume/Stop enabled</li>
 *   <li>{@code COMPLETED} — drift summary visible, controls reset to idle</li>
 * </ul>
 */
public enum SimControlState {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED;

    public boolean canTransitionTo(SimControlState target) {
        return switch (this) {
            case IDLE -> target == RUNNING;
            case RUNNING -> target == PAUSED || target == COMPLETED;
            case PAUSED -> target == RUNNING || target == COMPLETED;
            case COMPLETED -> target == IDLE || target == RUNNING;
        };
    }
}
