package dev.dunnam.diceanchors.sim.views;

/**
 * State machine for the {@link BenchmarkView} UI lifecycle.
 * <p>
 * Valid transitions:
 * <ul>
 *   <li>CONFIG → RUNNING (user starts a new experiment run)</li>
 *   <li>RUNNING → RESULTS (run completes)</li>
 *   <li>RUNNING → CONFIG (error recovery)</li>
 *   <li>RESULTS → CONFIG (user clears results to configure a new run)</li>
 *   <li>RESULTS → RESULTS (user loads a different result from history)</li>
 *   <li>CONFIG → RESULTS (user loads a historical result directly)</li>
 * </ul>
 */
public enum BenchmarkViewState {

    /**
     * Initial state: experiment parameters are being configured.
     */
    CONFIG,

    /**
     * An experiment run is in progress.
     */
    RUNNING,

    /**
     * A completed result is being displayed.
     */
    RESULTS;

    /**
     * Returns {@code true} if a transition from this state to {@code target} is permitted.
     *
     * @param target the desired next state
     *
     * @return {@code true} if the transition is valid
     */
    public boolean canTransitionTo(BenchmarkViewState target) {
        return switch (this) {
            case CONFIG -> target == RUNNING || target == RESULTS;
            case RUNNING -> target == RESULTS || target == CONFIG;
            case RESULTS -> target == CONFIG || target == RESULTS;
        };
    }
}
