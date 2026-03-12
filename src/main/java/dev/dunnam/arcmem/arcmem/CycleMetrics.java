package dev.dunnam.diceanchors.anchor;

import java.time.Duration;

/**
 * Aggregate metrics from a single proactive maintenance sweep execution.
 *
 * <p>Counters correspond to the 5-step sweep contract: audit, refresh, consolidate, prune,
 * validate. {@code anchorsAudited} is always >= all other counters.
 */
public record CycleMetrics(
        int anchorsAudited,
        int anchorsRefreshed,
        int anchorsConsolidated,
        int anchorsPruned,
        int validationViolations,
        SweepType sweepType,
        Duration duration
) {
    public static CycleMetrics empty() {
        return new CycleMetrics(0, 0, 0, 0, 0, SweepType.NONE, Duration.ZERO);
    }
}
