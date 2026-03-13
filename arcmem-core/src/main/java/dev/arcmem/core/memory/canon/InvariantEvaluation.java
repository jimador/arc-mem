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
