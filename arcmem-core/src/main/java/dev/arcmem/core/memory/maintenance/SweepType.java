package dev.arcmem.core.memory.maintenance;
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
 * Classification of a proactive maintenance sweep based on memory pressure level.
 *
 * <p>Sweep type determines audit depth: {@code FULL} sweeps invoke the optional LLM
 * audit pass for borderline units; {@code LIGHT} sweeps use heuristic scoring only.
 */
public enum SweepType {
    /** Pressure below light-sweep threshold — no sweep warranted. */
    NONE,
    /** Moderate pressure (>= light threshold, < full threshold) — heuristic audit only. */
    LIGHT,
    /** High pressure (>= full threshold) — heuristic audit plus optional LLM refinement. */
    FULL
}
