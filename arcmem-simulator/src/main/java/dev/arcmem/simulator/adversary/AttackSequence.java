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

/**
 * Identifies a multi-turn attack chain and the current phase within it.
 * <p>
 * Phases progress: SETUP → BUILD → PAYOFF.
 * A null {@code AttackSequence} on an {@link AttackPlan} indicates a standalone attack.
 *
 * @param id    opaque identifier for the sequence (UUID-based)
 * @param phase current phase: one of {@code "SETUP"}, {@code "BUILD"}, {@code "PAYOFF"}
 */
public record AttackSequence(String id, String phase) {

    public static final String SETUP = "SETUP";
    public static final String BUILD = "BUILD";
    public static final String PAYOFF = "PAYOFF";
}
