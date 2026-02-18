package dev.dunnam.diceanchors.anchor.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;

/**
 * Abstract base for all anchor lifecycle events.
 * <p>
 * Each event carries the context ID of the conversation or simulation in which
 * the lifecycle change occurred and a high-resolution timestamp.
 */
public abstract class AnchorLifecycleEvent extends ApplicationEvent {

    private final String contextId;
    private final Instant occurredAt;

    protected AnchorLifecycleEvent(Object source, String contextId) {
        super(source);
        this.contextId = contextId;
        this.occurredAt = Instant.now();
    }

    public String getContextId() {
        return contextId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
