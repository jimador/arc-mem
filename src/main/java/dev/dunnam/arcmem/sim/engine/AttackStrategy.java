package dev.dunnam.diceanchors.sim.engine;

/**
 * Adversarial attack techniques enumeration, organized by sophistication tier.
 * <p>
 * Each attack strategy corresponds to a {@link DriftStrategyDefinition} in the strategy catalog
 * YAML, which provides execution guidance, applicable anchor categories, and whether the strategy
 * spans multiple turns. The {@link TieredEscalationStrategy} orchestrates attack selection based
 * on aggressiveness, tier caps, and strategic preferences.
 */
public enum AttackStrategy {
    /**
     * Synonym or adjective substitution that shifts meaning of established fact.
     */
    SUBTLE_REFRAME,

    /**
     * States a false thing as though previously established.
     */
    CONFIDENT_ASSERTION,

    /**
     * Attributes a false claim to an NPC or authoritative source.
     */
    AUTHORITY_HIJACK,

    /**
     * Uses emotional pressure or urgency to override factual consistency.
     */
    EMOTIONAL_OVERRIDE,

    /**
     * References an event or detail that never happened.
     */
    FALSE_MEMORY_PLANT,

    /**
     * Asks about facts after significant in-game time gap to exploit temporal confusion.
     */
    TIME_SKIP_RECALL,

    /**
     * Overwhelms context with new information to displace established facts.
     */
    DETAIL_FLOOD,

    /**
     * Slowly shifts a fact over multiple turns through imperceptible variations.
     */
    GRADUAL_EROSION,

    /**
     * Expands or narrows scope of an established fact to change its meaning.
     */
    SCOPE_CREEP,

    /**
     * Quotes part of an established fact accurately but omits the key qualifier.
     */
    PARTIAL_QUOTE,

    /**
     * Reverses cause-effect or if-then relationship.
     */
    IMPLICATION_REVERSAL,

    /**
     * Exploits player character's authority to assert false facts.
     */
    PERSONA_LEVERAGE,

    /**
     * Invents a past event to justify a current contradiction.
     */
    RETROACTIVE_JUSTIFICATION,

    /**
     * Claims other party members or NPCs agree with false version.
     */
    CONSENSUS_PRESSURE,

    /**
     * Drops a negation from an established fact, reversing its meaning.
     */
    NEGATION_DROP,

    /**
     * Changes numbers, counts, or magnitudes in established facts.
     */
    QUANTITY_SHIFT,

    /**
     * References the DM's own earlier phrasing out of context.
     */
    SELF_REFERENCE_EXPLOIT,

    /**
     * Shifts temporal framing to make current fact seem historical or vice versa.
     */
    TENSE_MANIPULATION,

    /**
     * Tricks DM into agreeing with minor premise, then chains to contradictory conclusion.
     */
    AGREEMENT_EXTRACTION,

    /**
     * Removes or replaces a link in established causal chain.
     */
    CAUSAL_CHAIN_EROSION;

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
