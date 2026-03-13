package dev.arcmem.core.memory.canon;
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

import org.jspecify.annotations.Nullable;

/**
 * Data carrier for invariant violation details, passed between
 * {@link InvariantEvaluator} and event factory methods.
 */
public record InvariantViolationData(
        String ruleId,
        InvariantStrength strength,
        ProposedAction blockedAction,
        String constraintDescription,
        @Nullable String unitId
) {}
