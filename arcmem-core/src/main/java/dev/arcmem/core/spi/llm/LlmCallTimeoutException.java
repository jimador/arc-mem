package dev.arcmem.core.spi.llm;

/**
 * Thrown when an LLM call exceeds the configured per-call timeout.
 */
public class LlmCallTimeoutException extends RuntimeException {

    public LlmCallTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
