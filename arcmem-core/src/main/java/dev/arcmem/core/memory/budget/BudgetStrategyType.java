package dev.arcmem.core.memory.budget;
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

/** Selects the active {@link BudgetStrategy} implementation from configuration. */
public enum BudgetStrategyType {

    /** Count-only enforcement. Reproduces the original inline behavior. */
    COUNT,

    /**
     * Density-aware enforcement. Reduces effective budget when the conflict graph
     * exceeds the phase-transition threshold from Guo et al. (2025).
     */
    INTERFERENCE_DENSITY
}
