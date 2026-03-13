package dev.arcmem.core.memory.attention;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Published when attention metrics cross configured thresholds.
 * Separate from MemoryUnitLifecycleEvent — lifecycle events are inputs to the tracker;
 * attention signals are outputs (ATT3).
 */
public class AttentionSignal extends ApplicationEvent {

    private final @Nullable String unitId;
    private final String contextId;
    private final AttentionSignalType signalType;
    private final AttentionSnapshot snapshot;
    private final Instant occurredAt;

    public AttentionSignal(Object source, @Nullable String unitId, String contextId,
                           AttentionSignalType signalType, AttentionSnapshot snapshot) {
        super(source);
        this.unitId = unitId;
        this.contextId = contextId;
        this.signalType = signalType;
        this.snapshot = snapshot;
        this.occurredAt = Instant.now();
    }

    public @Nullable String getUnitId() {
        return unitId;
    }

    public String getContextId() {
        return contextId;
    }

    public AttentionSignalType getSignalType() {
        return signalType;
    }

    public AttentionSnapshot getSnapshot() {
        return snapshot;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
