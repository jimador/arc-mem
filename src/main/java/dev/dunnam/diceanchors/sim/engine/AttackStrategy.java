package dev.dunnam.diceanchors.sim.engine;

/**
 * Adversarial attack techniques used in ATTACK turns.
 */
public enum AttackStrategy {
    SUBTLE_REFRAME,
    CONFIDENT_ASSERTION,
    AUTHORITY_HIJACK,
    EMOTIONAL_OVERRIDE,
    FALSE_MEMORY_PLANT,
    TIME_SKIP_RECALL,
    DETAIL_FLOOD;

    /**
     * Parse an attack strategy from a string.
     *
     * @return the strategy, or null if the input is null/blank
     */
    public static AttackStrategy fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
