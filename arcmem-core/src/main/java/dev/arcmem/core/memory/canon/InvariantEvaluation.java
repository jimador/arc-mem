package dev.arcmem.core.memory.canon;

import java.util.List;

/**
 * Result of evaluating invariant rules against a proposed action.
 */
public record InvariantEvaluation(
        List<InvariantViolationData> violations,
        int checkedCount
) {
    public InvariantEvaluation {
        violations = List.copyOf(violations);
    }

    public boolean hasBlockingViolation() {
        return violations.stream()
                         .anyMatch(v -> v.strength() == InvariantStrength.MUST);
    }

    public boolean hasWarnings() {
        return violations.stream()
                         .anyMatch(v -> v.strength() == InvariantStrength.SHOULD);
    }
}
