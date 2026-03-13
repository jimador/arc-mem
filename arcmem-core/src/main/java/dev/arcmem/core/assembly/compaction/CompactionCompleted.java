package dev.arcmem.core.assembly.compaction;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Published when a compaction cycle completes successfully (i.e., the summary was applied).
 * Not published on validation failure or rollback.
 * <p>
 * Gated on {@link CompactionConfig#eventsEnabled()}.
 */
public final class CompactionCompleted extends ApplicationEvent {

    private final String contextId;
    private final String triggerReason;
    private final int tokensBefore;
    private final int tokensAfter;
    private final int lossCount;
    private final int retryCount;
    private final boolean fallbackUsed;
    private final boolean compactionApplied;
    private final Instant occurredAt;

    public CompactionCompleted(Object source, String contextId, String triggerReason,
                               int tokensBefore, int tokensAfter, int lossCount,
                               int retryCount, boolean fallbackUsed, boolean compactionApplied) {
        super(source);
        this.contextId = contextId;
        this.triggerReason = triggerReason;
        this.tokensBefore = tokensBefore;
        this.tokensAfter = tokensAfter;
        this.lossCount = lossCount;
        this.retryCount = retryCount;
        this.fallbackUsed = fallbackUsed;
        this.compactionApplied = compactionApplied;
        this.occurredAt = Instant.now();
    }

    public String getContextId() {
        return contextId;
    }

    public String getTriggerReason() {
        return triggerReason;
    }

    public int getTokensBefore() {
        return tokensBefore;
    }

    public int getTokensAfter() {
        return tokensAfter;
    }

    public int getLossCount() {
        return lossCount;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public boolean isCompactionApplied() {
        return compactionApplied;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
