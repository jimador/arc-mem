package dev.dunnam.diceanchors.assembly;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import dev.dunnam.diceanchors.prompt.PromptTemplates;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates D&D-aware narrative summaries using a {@link ChatModel}.
 * Results are cached in-memory by message content hash to avoid regeneration.
 */
@Component
public class SimSummaryGenerator {

    private static final Logger logger = LoggerFactory.getLogger(SimSummaryGenerator.class);

    private final ChatModel chatModel;
    private final Map<Integer, String> resultCache = new ConcurrentHashMap<>();

    public SimSummaryGenerator(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * Generate a concise narrative summary of the given messages.
     * The summary preserves key facts, character actions, and plot developments
     * relevant to a D&D campaign session.
     *
     * @param messages           the messages to summarize
     * @param contextDescription description of the context for the summary prompt
     *
     * @return a narrative summary string
     */
    public String generateSummary(List<String> messages, String contextDescription) {
        var cacheKey = messages.hashCode();
        var cached = resultCache.get(cacheKey);
        if (cached != null) {
            logger.debug("Returning cached summary for hash {}", cacheKey);
            return cached;
        }

        var conversationText = String.join("\n\n", messages);
        var promptText = PromptTemplates.render("prompts/sim/summary.jinja", Map.of(
                "context_description", contextDescription != null ? contextDescription : "",
                "conversation_text", conversationText));

        try {
            var response = chatModel.call(new Prompt(promptText));
            var summary = response.getResult().getOutput().getText();
            resultCache.put(cacheKey, summary);
            logger.info("Generated summary for {} messages ({} chars -> {} chars)",
                        messages.size(), conversationText.length(), summary.length());
            return summary;
        } catch (Exception e) {
            logger.error("Failed to generate summary: {}", e.getMessage(), e);
            return "[Summary generation failed: %s]".formatted(e.getMessage());
        }
    }

    /**
     * Clear the result cache. Useful for testing or memory management.
     */
    public void clearCache() {
        resultCache.clear();
    }
}
