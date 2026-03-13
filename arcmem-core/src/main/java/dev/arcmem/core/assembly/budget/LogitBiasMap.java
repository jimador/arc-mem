package dev.arcmem.core.assembly.budget;

import java.util.Map;

/**
 * OpenAI-compatible logit bias representation derived from memory unit constraints.
 * <p>
 * Token keys are strings (not integer IDs) to keep the core representation
 * model-agnostic. The API integration layer maps to integer IDs when needed.
 * <p>
 * Authority-tiered bias values are locked constants (not configurable):
 * CANON units receive the maximum bias of 100; RELIABLE units receive 50.
 * This mapping reflects the RFC 2119 compliance hierarchy (MUST vs SHOULD).
 *
 * @param tokenBiases     map of token string to bias value
 * @param constraintCount number of unit constraints that contributed to this map
 * @param coverage        average translation coverage across contributing constraints
 * @param overflowCount   tokens dropped because the 300-token limit was reached
 */
public record LogitBiasMap(
        Map<String, Integer> tokenBiases,
        int constraintCount,
        double coverage,
        int overflowCount
) {
    /**
     * OpenAI maximum logit_bias entries per request.
     */
    public static final int MAX_TOKENS = 300;

    /**
     * Bias value applied to tokens from CANON units (RFC 2119 MUST).
     */
    public static final int CANON_BIAS = 100;

    /**
     * Bias value applied to tokens from RELIABLE units (RFC 2119 SHOULD).
     */
    public static final int RELIABLE_BIAS = 50;

    /**
     * Empty map with no constraints.
     */
    public static LogitBiasMap empty() {
        return new LogitBiasMap(Map.of(), 0, 0.0, 0);
    }
}
