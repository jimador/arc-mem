package dev.dunnam.diceanchors.anchor;

/**
 * Classifies how a {@link MaintenanceStrategy} runs relative to the conversation turn cycle.
 *
 * <ul>
 *   <li>{@code REACTIVE} — maintenance runs synchronously after each turn via
 *       {@link MaintenanceStrategy#onTurnComplete}. No background sweep is triggered.
 *       This is the default, backward-compatible mode.</li>
 *   <li>{@code PROACTIVE} — maintenance runs in periodic sweeps driven by
 *       {@link MaintenanceStrategy#shouldRunSweep} and
 *       {@link MaintenanceStrategy#executeSweep}. Per-turn hook is a no-op.</li>
 *   <li>{@code HYBRID} — both mechanisms are active. The reactive hook runs after each
 *       turn; sweeps fire when the proactive trigger condition is met. Inspired by the
 *       wake/sleep memory consolidation cycle from Sleeping LLM (Guo et al., 2025).</li>
 * </ul>
 */
public enum MaintenanceMode {
    REACTIVE,
    PROACTIVE,
    HYBRID
}
