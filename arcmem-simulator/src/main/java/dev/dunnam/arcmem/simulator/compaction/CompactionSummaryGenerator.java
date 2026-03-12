package dev.dunnam.arcmem.simulator.compaction;

import dev.dunnam.arcmem.core.assembly.compaction.SummaryResult;
import dev.dunnam.arcmem.core.assembly.protection.ProtectedContent;
import dev.dunnam.arcmem.core.prompt.PromptPathConstants;
import dev.dunnam.arcmem.core.prompt.PromptTemplates;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Generates narrative summaries for context compaction using a {@link ChatModel}.
 * Results are cached in-memory by message content hash to avoid regeneration.
 * <p>
 * Supports configurable retry with exponential backoff and extractive fallback
 * when all LLM attempts fail.
 */
@Component
public class CompactionSummaryGenerator {

    private static final Logger logger = LoggerFactory.getLogger(CompactionSummaryGenerator.class);

    private final ChatModel chatModel;
    private final Map<Integer, String> resultCache = new ConcurrentHashMap<>();

    public CompactionSummaryGenerator(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * Convenience overload with no retry and no protected content.
     */
    public String generateSummary(List<String> messages, String contextDescription) {
        var result = generateSummary(messages, contextDescription, List.of(), 0, Duration.ZERO);
        return result.summary();
    }

    /**
     * Generate a concise narrative summary of the given messages with retry support.
     * The summary preserves key facts, character actions, and plot developments.
     * <p>
     * On LLM failure, retries up to {@code maxRetries} times with exponential backoff.
     * If all attempts fail, produces a deterministic extractive fallback from
     * {@code protectedContent} sorted by priority descending.
     *
     * @param protectedContent content that must survive compaction; used for extractive fallback
     * @param maxRetries       maximum retry attempts; 0 = single attempt with no retry
     * @param initialBackoff   backoff duration that doubles on each subsequent retry
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

    public void clearCache() {
        resultCache.clear();
    }
}
