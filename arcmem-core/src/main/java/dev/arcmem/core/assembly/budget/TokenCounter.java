package dev.arcmem.core.assembly.budget;

/**
 * Strategy interface for estimating LLM token count from prompt text.
 * <p>
 * Used by {@link PromptBudgetEnforcer} to enforce per-turn token limits on unit
 * context injection into the LLM prompt. Implementations may use simple character-based
 * estimates (e.g., assume 1 token per 4 characters) or sophisticated tokenization
 * (e.g., tiktoken-style wordpiece tokenization).
 * <p>
 * <strong>Thread-safety:</strong> implementations MUST be thread-safe. A single
 * {@code TokenCounter} instance MAY be shared across concurrent prompt assembly calls.
 */
public interface TokenCounter {

    /**
     * Estimate the number of LLM tokens in the given text.
     * <p>
     * Implementations MUST return a value {@code >= 0}. Returning {@code 0} for
     * {@code null} or empty input is acceptable. Implementations MUST NOT throw
     * exceptions for any input value.
     *
     * @param text the prompt text to estimate; MAY be null or empty
     *
     * @return estimated token count, always {@code >= 0}
     */
    int estimate(String text);
}
