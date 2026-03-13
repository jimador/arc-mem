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
 * Operator-defined invariant constraining unit state.
 * Evaluated on every lifecycle state change by {@link InvariantEvaluator}.
 *
 * <p>Invariants with {@link InvariantStrength#MUST} block the attempted action.
 * Invariants with {@link InvariantStrength#SHOULD} log a warning and allow the action.
 */
public sealed interface InvariantRule
        permits InvariantRule.AuthorityFloor,
                InvariantRule.EvictionImmunity,
                InvariantRule.MinAuthorityCount,
                InvariantRule.ArchiveProhibition {

    String id();
    InvariantStrength strength();
    /** Context scope. Null means global (applies to all contexts). */
    @Nullable String contextId();

    record AuthorityFloor(
            String id, InvariantStrength strength, @Nullable String contextId,
            String unitTextPattern, Authority minimumAuthority
    ) implements InvariantRule {}

    record EvictionImmunity(
            String id, InvariantStrength strength, @Nullable String contextId,
            String unitTextPattern
    ) implements InvariantRule {}

    record MinAuthorityCount(
            String id, InvariantStrength strength, @Nullable String contextId,
            Authority minimumAuthority, int minimumCount
    ) implements InvariantRule {}

    record ArchiveProhibition(
            String id, InvariantStrength strength, @Nullable String contextId,
            String unitTextPattern
    ) implements InvariantRule {}
}
