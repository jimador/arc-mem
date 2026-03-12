package dev.dunnam.diceanchors.anchor;

/**
 * Compliance obligation strength for anchor facts in prompts.
 * Maps to RFC 2119 keyword levels.
 */
public enum ComplianceStrength {
    /** "MUST be preserved" — absolute requirement (CANON, RELIABLE). */
    STRICT,
    /** "SHOULD be trusted" — strong recommendation (UNRELIABLE). */
    MODERATE,
    /** "MAY be reconsidered" — optional (PROVISIONAL). */
    PERMISSIVE
}
