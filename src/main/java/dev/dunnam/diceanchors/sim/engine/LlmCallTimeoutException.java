package dev.dunnam.diceanchors.sim.engine;

/**
 * Thrown when an LLM call exceeds the configured per-call timeout.
 */
public class LlmCallTimeoutException extends RuntimeException {

    public LlmCallTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
