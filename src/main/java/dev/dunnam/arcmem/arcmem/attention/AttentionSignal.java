package dev.dunnam.diceanchors.anchor.attention;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Published when attention metrics cross configured thresholds.
 * Separate from AnchorLifecycleEvent — lifecycle events are inputs to the tracker;
 * attention signals are outputs (ATT3).
 */
public class AttentionSignal extends ApplicationEvent {

    private final @Nullable String anchorId;
    private final String contextId;
    private final AttentionSignalType signalType;
    private final AttentionSnapshot snapshot;
    private final Instant occurredAt;

    public AttentionSignal(Object source, @Nullable String anchorId, String contextId,
                           AttentionSignalType signalType, AttentionSnapshot snapshot) {
        super(source);
        this.anchorId = anchorId;
        this.contextId = contextId;
        this.signalType = signalType;
        this.snapshot = snapshot;
        this.occurredAt = Instant.now();
    }

    public @Nullable String getAnchorId() { return anchorId; }
    public String getContextId() { return contextId; }
    public AttentionSignalType getSignalType() { return signalType; }
    public AttentionSnapshot getSnapshot() { return snapshot; }
    public Instant getOccurredAt() { return occurredAt; }
}
