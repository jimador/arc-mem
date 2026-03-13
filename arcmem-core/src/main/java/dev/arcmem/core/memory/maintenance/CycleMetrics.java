package dev.arcmem.core.memory.maintenance;

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
