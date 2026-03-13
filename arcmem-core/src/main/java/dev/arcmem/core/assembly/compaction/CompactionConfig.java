package dev.arcmem.core.assembly.compaction;

import java.util.List;

/**
 * Configuration for context compaction behavior.
 * Typically parsed from scenario YAML {@code compactionConfig} section.
 *
 * @param enabled            whether compaction is active
 * @param tokenThreshold     token count that triggers compaction
 * @param messageThreshold   message count that triggers compaction
 * @param forceAtTurns       turns at which compaction is forced regardless of thresholds
 * @param minMatchRatio      minimum ratio of significant words that must appear in summary [0.0, 1.0]
 * @param maxRetries         maximum number of LLM retry attempts on summary generation failure
 * @param retryBackoffMillis initial backoff duration in milliseconds (doubles per retry)
 * @param eventsEnabled      whether compaction lifecycle events are published
 */
public record CompactionConfig(
        boolean enabled,
        int tokenThreshold,
        int messageThreshold,
        List<Integer> forceAtTurns,
        double minMatchRatio,
        int maxRetries,
        long retryBackoffMillis,
        boolean eventsEnabled
) {
    public CompactionConfig {
        if (minMatchRatio < 0.0 || minMatchRatio > 1.0) {
            throw new IllegalArgumentException("minMatchRatio must be in [0.0, 1.0], was: " + minMatchRatio);
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0, was: " + maxRetries);
        }
        if (retryBackoffMillis <= 0) {
            throw new IllegalArgumentException("retryBackoffMillis must be > 0, was: " + retryBackoffMillis);
        }
    }

    /**
     * Disabled compaction config — used when no config section is present.
     */
    public static CompactionConfig disabled() {
        return new CompactionConfig(false, 0, 0, List.of(), 0.5, 2, 1000L, true);
    }
}
