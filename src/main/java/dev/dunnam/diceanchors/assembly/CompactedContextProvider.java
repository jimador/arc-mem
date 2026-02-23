package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.AnchorEngine;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks message history per context and triggers compaction when thresholds are exceeded.
 * Manages an in-memory summary store for prepending summaries to assembled context.
 * <p>
 * Compaction follows an atomic validate-before-clear pattern: the summary is validated
 * against protected anchors before any messages are removed. On validation failure,
 * messages remain untouched and compaction reports as not applied.
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
    private final ApplicationEventPublisher eventPublisher;

    public CompactedContextProvider(
            List<ProtectedContentProvider> protectedContentProviders,
            SimSummaryGenerator summaryGenerator,
            AnchorEngine anchorEngine,
            ApplicationEventPublisher eventPublisher) {
        this.protectedContentProviders = protectedContentProviders;
        this.summaryGenerator = summaryGenerator;
        this.anchorEngine = anchorEngine;
        this.eventPublisher = eventPublisher;
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
     * Execute compaction with atomic validate-before-clear semantics.
     * <p>
     * Flow:
     * <ol>
     *   <li>Snapshot messages for safety</li>
     *   <li>Collect protected content from providers</li>
     *   <li>Generate summary (with retry and extractive fallback)</li>
     *   <li>Validate summary against protected anchors BEFORE clearing messages</li>
     *   <li>If validation passes: clear messages and store summary</li>
     *   <li>If validation fails: leave messages untouched, report as not applied</li>
     *   <li>Publish {@link CompactionCompleted} event if applied and events enabled</li>
     *   <li>Set OTEL span attributes</li>
     * </ol>
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

        // Snapshot for safety — messages are not cleared until validation passes
        var backup = List.copyOf(messages);

        // Collect protected content from all providers
        var protectedContent = new ArrayList<ProtectedContent>();
        var protectedIds = new ArrayList<String>();
        for (var provider : protectedContentProviders) {
            var content = provider.getProtectedContent(contextId);
            for (var pc : content) {
                protectedContent.add(pc);
                protectedIds.add(pc.id());
            }
        }

        // Generate summary with retry support
        var summaryResult = summaryGenerator.generateSummary(
                backup,
                "D&D session context " + contextId,
                protectedContent,
                config.maxRetries(),
                Duration.ofMillis(config.retryBackoffMillis()));

        // Validate BEFORE clearing — anchors must survive in the summary
        var anchors = anchorEngine.inject(contextId);
        var lossEvents = CompactionValidator.validate(
                summaryResult.summary(), anchors, config.minMatchRatio());

        var compactionApplied = lossEvents.isEmpty();
        int tokensAfter;

        if (compactionApplied) {
            // Validation passed — commit: clear messages and store summary
            var startTurn = 1;
            var endTurn = backup.size();
            var summaryKey = contextId + ":" + startTurn + "-" + endTurn;
            summaryStore.put(summaryKey, summaryResult.summary());
            messages.clear();
            tokensAfter = estimateTokens(List.of(summaryResult.summary()));

            logger.info("Compacted context {} — reason={}, tokens {}->{}. Protected {} items in {}ms",
                        contextId, triggerReason, tokensBefore, tokensAfter,
                        protectedIds.size(), System.currentTimeMillis() - startMs);
        } else {
            // Validation failed — messages remain untouched
            tokensAfter = tokensBefore;
            logger.warn("Compaction rejected for {}: {} loss events — {}",
                        contextId, lossEvents.size(),
                        lossEvents.stream().map(CompactionLossEvent::anchorId).toList());
        }

        var durationMs = System.currentTimeMillis() - startMs;

        var result = new CompactionResult(
                triggerReason,
                tokensBefore,
                tokensAfter,
                List.copyOf(protectedIds),
                summaryResult.summary(),
                durationMs,
                lossEvents,
                compactionApplied,
                summaryResult.retryCount(),
                summaryResult.fallbackUsed());

        // Publish event only when compaction was applied and events are enabled
        if (compactionApplied && config.eventsEnabled()) {
            eventPublisher.publishEvent(new CompactionCompleted(
                    this, contextId, triggerReason,
                    tokensBefore, tokensAfter, lossEvents.size(),
                    summaryResult.retryCount(), summaryResult.fallbackUsed(), true));
        }

        // OTEL span attributes
        var span = Span.current();
        span.setAttribute("compaction.trigger_reason", triggerReason);
        span.setAttribute("compaction.tokens_before", tokensBefore);
        span.setAttribute("compaction.tokens_after", tokensAfter);
        span.setAttribute("compaction.loss_count", (long) lossEvents.size());
        span.setAttribute("compaction.retry_count", (long) summaryResult.retryCount());
        span.setAttribute("compaction.fallback_used", summaryResult.fallbackUsed());
        span.setAttribute("compaction.applied", compactionApplied);

        return result;
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
