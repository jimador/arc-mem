package dev.dunnam.diceanchors.assembly;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import dev.dunnam.diceanchors.prompt.PromptPathConstants;
import dev.dunnam.diceanchors.prompt.PromptTemplates;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Generates D&D-aware narrative summaries using a {@link ChatModel}.
 * Results are cached in-memory by message content hash to avoid regeneration.
 * <p>
 * Supports configurable retry with exponential backoff and extractive fallback
 * when all LLM attempts fail.
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
     * Convenience overload with no retry, no protected content.
     * Delegates to the full method with zero retries and empty protected content.
     *
     * @param messages           the messages to summarize
     * @param contextDescription description of the context for the summary prompt
     *
     * @return a narrative summary string
     */
    public String generateSummary(List<String> messages, String contextDescription) {
        var result = generateSummary(messages, contextDescription, List.of(), 0, Duration.ZERO);
        return result.summary();
    }

    /**
     * Generate a concise narrative summary of the given messages with retry support.
     * The summary preserves key facts, character actions, and plot developments
     * relevant to a D&D campaign session.
     * <p>
     * On LLM failure, retries up to {@code maxRetries} times with exponential backoff.
     * If all attempts fail, produces a deterministic extractive fallback from
     * {@code protectedContent} sorted by priority descending.
     *
     * @param messages           the messages to summarize
     * @param contextDescription description of the context for the summary prompt
     * @param protectedContent   content that should survive compaction (used for fallback)
     * @param maxRetries         maximum number of retry attempts (0 = single attempt, no retry)
     * @param initialBackoff     initial backoff duration between retries (doubles per attempt)
     *
     * @return a {@link SummaryResult} with the summary text, retry count, and fallback flag
     */
    public SummaryResult generateSummary(List<String> messages, String contextDescription,
                                         List<ProtectedContent> protectedContent,
                                         int maxRetries, Duration initialBackoff) {
        var cacheKey = messages.hashCode();
        var cached = resultCache.get(cacheKey);
        if (cached != null) {
            logger.debug("Returning cached summary for hash {}", cacheKey);
            return new SummaryResult(cached, 0, false);
        }

        var conversationText = String.join("\n\n", messages);
        var promptText = PromptTemplates.render(PromptPathConstants.SIM_SUMMARY, Map.of(
                "context_description", contextDescription != null ? contextDescription : "",
                "conversation_text", conversationText));

        var totalAttempts = 1 + maxRetries;
        for (int attempt = 0; attempt < totalAttempts; attempt++) {
            try {
                var response = chatModel.call(new Prompt(promptText));
                var summary = response.getResult().getOutput().getText();
                resultCache.put(cacheKey, summary);
                logger.info("Generated summary for {} messages ({} chars -> {} chars) on attempt {}",
                            messages.size(), conversationText.length(), summary.length(), attempt);
                return new SummaryResult(summary, attempt, false);
            } catch (Exception e) {
                logger.warn("Summary generation attempt {} failed: {}", attempt, e.getMessage());
                if (attempt < maxRetries) {
                    var backoffMs = initialBackoff.toMillis() * (1L << attempt);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.warn("Retry backoff interrupted on attempt {}", attempt);
                        break;
                    }
                }
            }
        }

        // All attempts failed — extractive fallback
        var fallback = buildExtractiveFallback(protectedContent);
        logger.warn("All {} LLM attempts failed, using extractive fallback ({} chars)",
                    totalAttempts, fallback.length());
        return new SummaryResult(fallback, maxRetries, true);
    }

    private String buildExtractiveFallback(List<ProtectedContent> protectedContent) {
        if (protectedContent == null || protectedContent.isEmpty()) {
            return "";
        }
        return protectedContent.stream()
                .sorted(Comparator.comparingInt(ProtectedContent::priority).reversed())
                .map(ProtectedContent::text)
                .collect(Collectors.joining(" "));
    }

    /**
     * Clear the result cache. Useful for testing or memory management.
     */
    public void clearCache() {
        resultCache.clear();
    }
}
