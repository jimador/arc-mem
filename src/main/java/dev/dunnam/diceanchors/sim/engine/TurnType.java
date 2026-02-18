package dev.dunnam.diceanchors.sim.engine;

/**
 * Semantic classification for simulation turns.
 */
public enum TurnType {
    WARM_UP,
    ESTABLISH,
    ATTACK,
    DISPLACEMENT,
    DRIFT,
    RECALL_PROBE;

    /**
     * Parse a turn type from a string, falling back to {@code ESTABLISH} on null or unknown.
     */
    public static TurnType fromString(String value) {
        if (value == null || value.isBlank()) {
            return ESTABLISH;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ESTABLISH;
        }
    }

    /**
     * Returns true if this turn type warrants drift evaluation against ground truth.
     */
    public boolean requiresEvaluation() {
        return this == ATTACK || this == DISPLACEMENT || this == DRIFT || this == RECALL_PROBE;
    }
}
