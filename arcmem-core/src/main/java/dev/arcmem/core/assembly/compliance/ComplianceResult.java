package dev.arcmem.core.assembly.compliance;
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

import java.time.Duration;
import java.util.List;

/**
 * Result of a {@link ComplianceEnforcer#enforce(ComplianceContext)} call.
 *
 * @param compliant          true when no violations were detected
 * @param violations         list of violations found; empty when compliant
 * @param suggestedAction    what the caller should do with the validated response
 * @param validationDuration wall-clock time consumed by validation
 */
public record ComplianceResult(
        boolean compliant,
        List<ComplianceViolation> violations,
        ComplianceAction suggestedAction,
        Duration validationDuration
) {

    /**
     * Returns a compliant result with no violations and {@link ComplianceAction#ACCEPT}.
     * Used by zero-cost enforcers (e.g., {@link PromptInjectionEnforcer}) and as the
     * fast-path when no units need enforcement.
     */
    public static ComplianceResult compliant(Duration duration) {
        return new ComplianceResult(true, List.of(), ComplianceAction.ACCEPT, duration);
    }
}
