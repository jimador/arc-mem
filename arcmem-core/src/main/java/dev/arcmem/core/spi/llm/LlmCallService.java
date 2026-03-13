package dev.arcmem.core.spi.llm;

import dev.arcmem.core.config.ArcMemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;

/**
 * Executes blocking LLM calls on virtual threads with per-call timeout enforcement.
 * <p>
 * Uses Java 25 {@link StructuredTaskScope} for each invocation so that timeout
 * and cancellation semantics are lexically scoped. Safe for concurrent use from
 * multiple callers: no shared mutable state.
 * <p>
 * Batch calls use 2× the standard timeout to accommodate larger expected response times.
 */
@Service
public class LlmCallService {

    private static final Logger logger = LoggerFactory.getLogger(LlmCallService.class);

    private final ChatModel chatModel;
    private final Duration callTimeout;
    private final Duration batchCallTimeout;

    public LlmCallService(ChatModel chatModel, ArcMemProperties properties) {
        this.chatModel = chatModel;
        this.callTimeout = Duration.ofSeconds(properties.llmCall().callTimeoutSeconds());
        this.batchCallTimeout = this.callTimeout.multipliedBy(2);
    }

    /**
     * Calls the LLM with the given system and user prompts.
     * Blocks until a response is received or the per-call timeout elapses.
     *
     * @throws LlmCallTimeoutException if the call exceeds the configured timeout
     */
    public String call(String systemPrompt, String userPrompt) {
        logger.debug("LLM call (timeout={}s)", callTimeout.toSeconds());
        return executeWithTimeout(systemPrompt, userPrompt, callTimeout);
    }

    /**
     * Calls the LLM with a prompt that semantically contains multiple items (a batch).
     * Uses 2× the standard timeout to allow for the larger expected processing time.
     *
     * @throws LlmCallTimeoutException if the call exceeds the batch timeout
     */
    public String callBatched(String systemPrompt, String userPrompt) {
        logger.debug("LLM batched call (timeout={}s)", batchCallTimeout.toSeconds());
        return executeWithTimeout(systemPrompt, userPrompt, batchCallTimeout);
    }

    private String executeWithTimeout(String systemPrompt, String userPrompt, Duration timeout) {
        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<String> awaitAllSuccessfulOrThrow(),
                cfg -> cfg.withTimeout(timeout))) {
            var task = scope.fork(() -> {
                var prompt = new Prompt(List.of(
                        new SystemMessage(systemPrompt),
                        new UserMessage(userPrompt)
                ));
                return chatModel.call(prompt).getResult().getOutput().getText();
            });
            scope.join();
            return task.get();
        } catch (StructuredTaskScope.FailedException e) {
            var cause = e.getCause();
            if (cause instanceof LlmCallTimeoutException lte) {
                throw lte;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("LLM call failed: " + (cause != null ? cause.getMessage() : e.getMessage()), cause != null ? cause : e);
        } catch (StructuredTaskScope.TimeoutException e) {
            throw new LlmCallTimeoutException(
                    "LLM call timed out after " + timeout.toSeconds() + "s", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("LLM call interrupted", e);
        }
    }
}
