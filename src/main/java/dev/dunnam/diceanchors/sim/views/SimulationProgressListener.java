package dev.dunnam.diceanchors.sim.views;

import dev.dunnam.diceanchors.sim.engine.SimulationProgress;

/**
 * Listener interface for panels that react to simulation progress events.
 * <p>
 * Panels implement this interface and register with {@link ProgressDispatcher}
 * to receive progress updates without SimulationView reaching into their internals.
 * All methods default to no-op so panels only override what they need.
 */
public interface SimulationProgressListener {

    /**
     * Called when a turn starts: player message is ready, DM is still thinking.
     * Typically used to show the player bubble and thinking indicator.
     */
    default void onTurnStarted(SimulationProgress progress) {}

    /**
     * Called when a turn completes: DM response has arrived.
     * Typically used to show the DM bubble, update verdicts, refresh panels.
     */
    default void onTurnCompleted(SimulationProgress progress) {}

    /**
     * Called when the simulation ends (complete or cancelled).
     * Typically used to show final results or summary.
     */
    default void onSimulationCompleted(SimulationProgress progress) {}
}
