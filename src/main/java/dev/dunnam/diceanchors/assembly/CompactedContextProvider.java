package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.AnchorEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks message history per context and triggers compaction when thresholds are exceeded.
 * Manages an in-memory summary store for prepending summaries to assembled context.
 * <p>
 * Token estimation uses a simple heuristic of ~4 characters per token.
 */
@Component
public class CompactedContextProvider {

    private static final Logger logger = LoggerFactory.getLogger(CompactedContextProvider.class);
    private static final int CHARS_PER_TOKEN = 4;

    private final Map<String, List<String>> messageHistory = new ConcurrentHashMap<>();
    private final Map<String, String> summaryStore = new ConcurrentHashMap<>();
    private final List<ProtectedContentProvider> protectedContentProviders;
    private final SimSummaryGenerator summaryGenerator;
    private final AnchorEngine anchorEngine;

    public CompactedContextProvider(
            List<ProtectedContentProvider> protectedContentProviders,
            SimSummaryGenerator summaryGenerator,
            AnchorEngine anchorEngine) {
        this.protectedContentProviders = protectedContentProviders;
        this.summaryGenerator = summaryGenerator;
        this.anchorEngine = anchorEngine;
    }

    /**
     * Add a message to the history for the given context.
     *
     * @param contextId the conversation or session context
     * @param message   the message text to track
     */
    public void addMessage(String contextId, String message) {
        messageHistory.computeIfAbsent(contextId, k -> Collections.synchronizedList(new ArrayList<>()))
                      .add(message);
    }

    /**
     * Determine whether compaction should be triggered for the given context.
     *
     * @param contextId the conversation or session context
     * @param config    compaction configuration
     *
     * @return true if compaction should run
     */
    public boolean shouldCompact(String contextId, CompactionConfig config) {
        if (!config.enabled()) {
            return false;
        }
        var messages = messageHistory.getOrDefault(contextId, List.of());
        if (config.messageThreshold() > 0 && messages.size() >= config.messageThreshold()) {
            return true;
        }
        if (config.tokenThreshold() > 0 && estimateTokens(messages) >= config.tokenThreshold()) {
            return true;
        }
        return false;
    }

    /**
     * Check if the current turn is a forced compaction turn.
     *
     * @param currentTurn the current turn number
     * @param config      compaction configuration
     *
     * @return true if compaction is forced at this turn
     */
    public boolean isForcedTurn(int currentTurn, CompactionConfig config) {
        return config.enabled() && config.forceAtTurns().contains(currentTurn);
    }

    /**
     * Execute compaction: generate a summary of existing messages, store it,
     * and clear the compacted messages while preserving protected content.
     *
     * @param contextId     the conversation or session context
     * @param config        compaction configuration
     * @param triggerReason why compaction was triggered
     *
     * @return the compaction result with metrics
     */
    public CompactionResult compact(String contextId, CompactionConfig config, String triggerReason) {
        var startMs = System.currentTimeMillis();
        var messages = messageHistory.getOrDefault(contextId, List.of());
        var tokensBefore = estimateTokens(messages);

        var protectedIds = new ArrayList<String>();
        for (var provider : protectedContentProviders) {
            var content = provider.getProtectedContent(contextId);
            for (var pc : content) {
                protectedIds.add(pc.id());
            }
        }

        var summary = summaryGenerator.generateSummary(List.copyOf(messages), "D&D session context " + contextId);

        var startTurn = 1;
        var endTurn = messages.size();
        var summaryKey = contextId + ":" + startTurn + "-" + endTurn;
        summaryStore.put(summaryKey, summary);

        messages.clear();

        // Validate that protected anchors survived compaction
        var protectedAnchors = anchorEngine.inject(contextId);
        var lossEvents = CompactionValidator.validate(summary, protectedAnchors);

        var tokensAfter = estimateTokens(List.of(summary));
        var durationMs = System.currentTimeMillis() - startMs;

        if (!lossEvents.isEmpty()) {
            logger.warn("Compaction lost {} anchors for context {}: {}",
                        lossEvents.size(), contextId,
                        lossEvents.stream().map(CompactionLossEvent::anchorId).toList());
        }

        logger.info("Compacted context {} — reason={}, tokens {}->{}. Protected {} items in {}ms",
                    contextId, triggerReason, tokensBefore, tokensAfter, protectedIds.size(), durationMs);

        return new CompactionResult(
                triggerReason,
                tokensBefore,
                tokensAfter,
                List.copyOf(protectedIds),
                summary,
                durationMs,
                lossEvents
        );
    }

    /**
     * Convenience overload that determines the trigger reason automatically.
     */
    public CompactionResult compact(String contextId, CompactionConfig config) {
        var messages = messageHistory.getOrDefault(contextId, List.of());
        String reason;
        if (config.tokenThreshold() > 0 && estimateTokens(messages) >= config.tokenThreshold()) {
            reason = "token_threshold";
        } else if (config.messageThreshold() > 0 && messages.size() >= config.messageThreshold()) {
            reason = "message_threshold";
        } else {
            reason = "forced_turn";
        }
        return compact(contextId, config, reason);
    }

    /**
     * Return all stored summaries for the given context, suitable for prepending to context.
     *
     * @param contextId the conversation or session context
     *
     * @return list of summary strings, ordered by storage time
     */
    public List<String> getSummaries(String contextId) {
        var result = new ArrayList<String>();
        for (var entry : summaryStore.entrySet()) {
            if (entry.getKey().startsWith(contextId + ":")) {
                result.add(entry.getValue());
            }
        }
        return List.copyOf(result);
    }

    /**
     * Return the current message count for the given context.
     */
    public int getMessageCount(String contextId) {
        return messageHistory.getOrDefault(contextId, List.of()).size();
    }

    /**
     * Return the estimated token count for the given context.
     */
    public int getEstimatedTokens(String contextId) {
        return estimateTokens(messageHistory.getOrDefault(contextId, List.of()));
    }

    /**
     * Clear all state for a context (used during sim cleanup).
     */
    public void clearContext(String contextId) {
        messageHistory.remove(contextId);
        summaryStore.keySet().removeIf(key -> key.startsWith(contextId + ":"));
    }

    private int estimateTokens(List<String> messages) {
        var totalChars = 0;
        for (var msg : messages) {
            totalChars += msg.length();
        }
        return totalChars / CHARS_PER_TOKEN;
    }
}
