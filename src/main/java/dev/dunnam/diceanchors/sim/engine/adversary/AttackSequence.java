package dev.dunnam.diceanchors.sim.engine.adversary;

/**
 * Identifies a multi-turn attack chain and the current phase within it.
 * <p>
 * Phases progress: SETUP → BUILD → PAYOFF.
 * A null {@code AttackSequence} on an {@link AttackPlan} indicates a standalone attack.
 *
 * @param id    opaque identifier for the sequence (UUID-based)
 * @param phase current phase: one of {@code "SETUP"}, {@code "BUILD"}, {@code "PAYOFF"}
 */
public record AttackSequence(String id, String phase) {

    public static final String SETUP = "SETUP";
    public static final String BUILD = "BUILD";
    public static final String PAYOFF = "PAYOFF";
}
