package dev.arcmem.core.spi.llm;
import dev.arcmem.core.memory.budget.*;
import dev.arcmem.core.memory.canon.*;
import dev.arcmem.core.memory.conflict.*;
import dev.arcmem.core.memory.engine.*;
import dev.arcmem.core.memory.maintenance.*;
import dev.arcmem.core.memory.model.*;
import dev.arcmem.core.memory.mutation.*;
import dev.arcmem.core.memory.trust.*;
import dev.arcmem.core.assembly.budget.*;
import dev.arcmem.core.assembly.compaction.*;
import dev.arcmem.core.assembly.compliance.*;
import dev.arcmem.core.assembly.protection.*;
import dev.arcmem.core.assembly.retrieval.*;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Delegating wrapper around {@link ChatModel} that supports observation and
 * per-turn model switching.
 * <p>
 * The observation registry is wired in so that Spring AI observations fire
 * automatically on each LLM call. Model switching is stubbed — it logs the
 * request and stores the name but does not yet swap the underlying model.
 */
public class ChatModelHolder {

    private static final Logger logger = LoggerFactory.getLogger(ChatModelHolder.class);

    private final ChatModel delegate;
    private final ObservationRegistry observationRegistry;
    private volatile String activeModelName = "default";

    public ChatModelHolder(ChatModel delegate, ObservationRegistry observationRegistry) {
        this.delegate = delegate;
        this.observationRegistry = observationRegistry;
    }

    public ChatResponse call(Prompt prompt) {
        return delegate.call(prompt);
    }

    /**
     * Request a model switch for subsequent calls.
     * <p>
     * Currently logs the request and stores the name. Actual model switching
     * requires a ChatModel factory which is not yet available.
     *
     * @param modelName the desired model identifier (e.g., "gpt-4o")
     */
    public void switchModel(String modelName) {
        logger.info("Model switch requested: {} -> {}", activeModelName, modelName);
        this.activeModelName = modelName;
    }

    public String getActiveModelName() {
        return activeModelName;
    }

    public ObservationRegistry getObservationRegistry() {
        return observationRegistry;
    }
}
