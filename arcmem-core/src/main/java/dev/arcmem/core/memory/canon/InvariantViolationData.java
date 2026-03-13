package dev.arcmem.core.memory.canon;

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
