package dev.dunnam.diceanchors.sim.views;

import dev.dunnam.diceanchors.sim.engine.SimulationProgress;

import java.util.ArrayList;
import java.util.List;

/**
 * UI-scoped dispatcher that routes {@link SimulationProgress} events to
 * registered {@link SimulationProgressListener} instances.
 * <p>
 * Not a Spring bean — owned by {@link SimulationView} and scoped to the UI session.
 * Listeners are notified in registration order.
 */
public class ProgressDispatcher {

    private final List<SimulationProgressListener> listeners = new ArrayList<>();

    public void addListener(SimulationProgressListener listener) {
        listeners.add(listener);
    }

    /**
     * Dispatch a progress event to all registered listeners.
     * <p>
     * Routing logic:
     * <ul>
     *   <li>{@code complete == true} → {@link SimulationProgressListener#onSimulationCompleted}</li>
     *   <li>{@code lastDmResponse != null} → {@link SimulationProgressListener#onTurnCompleted}</li>
     *   <li>{@code lastPlayerMessage != null} → {@link SimulationProgressListener#onTurnStarted}</li>
     * </ul>
     */
    public void dispatch(SimulationProgress progress) {
        if (progress.complete()) {
            for (var listener : listeners) {
                listener.onSimulationCompleted(progress);
            }
        } else if (progress.lastDmResponse() != null) {
            for (var listener : listeners) {
                listener.onTurnCompleted(progress);
            }
        } else if (progress.lastPlayerMessage() != null) {
            for (var listener : listeners) {
                listener.onTurnStarted(progress);
            }
        }
    }
}
