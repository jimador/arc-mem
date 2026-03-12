package dev.dunnam.diceanchors.sim.engine;

/**
 * Formalizes the simulation lifecycle state machine.
 * <p>
 * Valid transitions:
 * <pre>
 *   IDLE     -> RUNNING
 *   RUNNING  -> PAUSED, COMPLETED
 *   PAUSED   -> RUNNING, COMPLETED
 *   COMPLETED -> IDLE, RUNNING
 * </pre>
 * <p>
 * Side effects per state:
 * <ul>
 *   <li>{@code IDLE} — controls enabled, panels reset</li>
 *   <li>{@code RUNNING} — controls disabled (except Pause/Stop), panels updating</li>
 *   <li>{@code PAUSED} — manipulation panel visible, Pause disabled, Resume/Stop enabled</li>
 *   <li>{@code COMPLETED} — drift summary visible, controls reset to idle</li>
 * </ul>
 */
public enum SimControlState {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED;

    public boolean canTransitionTo(SimControlState target) {
        return switch (this) {
            case IDLE -> target == RUNNING;
            case RUNNING -> target == PAUSED || target == COMPLETED;
            case PAUSED -> target == RUNNING || target == COMPLETED;
            case COMPLETED -> target == IDLE || target == RUNNING;
        };
    }
}
