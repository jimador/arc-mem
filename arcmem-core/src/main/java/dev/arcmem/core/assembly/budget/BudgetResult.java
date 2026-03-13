package dev.arcmem.core.assembly.budget;
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
 * Result of enforcing a prompt token budget for unit assembly.
 *
 * @param included        units retained in the prompt
 * @param excluded        units dropped to fit budget constraints
 * @param estimatedTokens estimated total tokens for the final unit block
 * @param budgetExceeded  true when mandatory content exceeded budget or units were dropped
 */
public record BudgetResult(
        List<MemoryUnit> included,
        List<MemoryUnit> excluded,
        int estimatedTokens,
        boolean budgetExceeded
) {
}
