package dev.arcmem.core.memory.maintenance;

import java.time.Duration;

/**
 * Outcome record for a proactive maintenance sweep executed by {@link MaintenanceStrategy#executeSweep}.
 *
 * <p>The five counters correspond to the sweep contract steps drawn from the Sleeping LLM paper
 * (Guo et al., 2025) sleep architecture: audit, refresh, consolidate, prune, validate.
 * Not all steps modify units; {@code unitsAudited} will always be &ge; all other counters.
 *
 * <p>An empty result (all counters zero, duration near zero) indicates a no-op sweep —
 * the normal return value from {@link ReactiveMaintenanceStrategy} and
 * {@link ProactiveMaintenanceStrategy} before F07 is implemented.
 */
public record SweepResult(
        int unitsAudited,
        int unitsRefreshed,
        int unitsPruned,
        int unitsValidated,
        Duration duration,
        String summary
) {

    /**
     * An empty sweep result representing a no-op execution.
     */
    public static SweepResult empty() {
        return new SweepResult(0, 0, 0, 0, Duration.ZERO, "No sweep executed");
    }

    /**
     * An empty sweep result with a custom summary message.
     */
    public static SweepResult empty(String summary) {
        return new SweepResult(0, 0, 0, 0, Duration.ZERO, summary);
    }
}
