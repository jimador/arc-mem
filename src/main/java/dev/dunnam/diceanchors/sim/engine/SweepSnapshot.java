package dev.dunnam.diceanchors.sim.engine;

/**
 * Captures whether a proactive maintenance sweep ran during a simulation turn and its outcome.
 */
public record SweepSnapshot(
        boolean executed,
        String summary
) {
    public static SweepSnapshot none() {
        return new SweepSnapshot(false, "");
    }
}
