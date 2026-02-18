package dev.dunnam.diceanchors.assembly;

import java.util.List;

/**
 * Configuration for context compaction behavior.
 * Typically parsed from scenario YAML {@code compactionConfig} section.
 *
 * @param enabled          whether compaction is active
 * @param tokenThreshold   token count that triggers compaction
 * @param messageThreshold message count that triggers compaction
 * @param forceAtTurns     turns at which compaction is forced regardless of thresholds
 */
public record CompactionConfig(
        boolean enabled,
        int tokenThreshold,
        int messageThreshold,
        List<Integer> forceAtTurns
) {
    /**
     * Disabled compaction config — used when no config section is present.
     */
    public static CompactionConfig disabled() {
        return new CompactionConfig(false, 0, 0, List.of());
    }
}
