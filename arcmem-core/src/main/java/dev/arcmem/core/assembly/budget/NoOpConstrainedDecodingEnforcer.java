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

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

/**
 * Stub implementation of {@link ConstrainedDecodingEnforcer} that returns an
 * unconstrained mask — all tokens allowed.
 * <p>
 * This is a placeholder for when local model infrastructure (vLLM, HF LogitsProcessor)
 * is not available. It satisfies the interface contract for testing and wiring without
 * any computational overhead beyond array allocation.
 */
@Component
public class NoOpConstrainedDecodingEnforcer implements ConstrainedDecodingEnforcer {

    @Override
    public ComplianceResult enforce(ComplianceContext context) {
        return ComplianceResult.compliant(Duration.ZERO);
    }

    @Override
    public ConstraintMask computeConstraintMask(SemanticUnitConstraintIndex index, int vocabSize) {
        var mask = new boolean[vocabSize];
        Arrays.fill(mask, true);
        return new ConstraintMask(mask, 0, vocabSize);
    }
}
