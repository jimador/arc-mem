package dev.arcmem.core.memory.maintenance;
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

/**
 * Aggregate metrics from a single proactive maintenance sweep execution.
 *
 * <p>Counters correspond to the 5-step sweep contract: audit, refresh, consolidate, prune,
 * validate. {@code unitsAudited} is always >= all other counters.
 */
public record CycleMetrics(
        int unitsAudited,
        int unitsRefreshed,
        int unitsConsolidated,
        int unitsPruned,
        int validationViolations,
        SweepType sweepType,
        Duration duration
) {
    public static CycleMetrics empty() {
        return new CycleMetrics(0, 0, 0, 0, 0, SweepType.NONE, Duration.ZERO);
    }
}
