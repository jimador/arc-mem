package dev.dunnam.diceanchors.anchor;

import org.jspecify.annotations.Nullable;

/**
 * Operator-defined invariant constraining anchor state.
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
            String anchorTextPattern, Authority minimumAuthority
    ) implements InvariantRule {}

    record EvictionImmunity(
            String id, InvariantStrength strength, @Nullable String contextId,
            String anchorTextPattern
    ) implements InvariantRule {}

    record MinAuthorityCount(
            String id, InvariantStrength strength, @Nullable String contextId,
            Authority minimumAuthority, int minimumCount
    ) implements InvariantRule {}

    record ArchiveProhibition(
            String id, InvariantStrength strength, @Nullable String contextId,
            String anchorTextPattern
    ) implements InvariantRule {}
}
