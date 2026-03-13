package dev.arcmem.simulator.engine;
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

import dev.arcmem.core.spi.llm.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

/**
 * Captures post-generation compliance validation results for a single simulation turn.
 * In simulation mode, enforcement always accepts the response but captures violations
 * for observability.
 */
public record ComplianceSnapshot(
        int violationCount,
        String suggestedAction,
        boolean wouldHaveRetried,
        long validationMs
) {
    public static ComplianceSnapshot none() {
        return new ComplianceSnapshot(0, "", false, 0L);
    }
}
