package dev.arcmem.simulator.adversary;
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


import java.util.List;

/**
 * Contract for adaptive adversary implementations.
 * <p>
 * Implementations receive full unit state at each turn and the run's
 * {@link AttackHistory}, and produce a non-null {@link AttackPlan}.
 */
public interface AdversaryStrategy {

    /**
     * Select an attack plan for the current turn.
     *
     * @param active     currently active units (highest-rank first, from {@code ArcMemEngine.inject()})
     * @param conflicted units currently in conflict state (from {@code ArcMemEngine.detectConflicts()})
     * @param history    outcomes recorded so far in this simulation run
     *
     * @return a non-null {@link AttackPlan} with at least one strategy and at least one target
     */
    AttackPlan selectAttack(List<MemoryUnit> active, List<MemoryUnit> conflicted, AttackHistory history);
}
