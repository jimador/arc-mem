package dev.dunnam.diceanchors.anchor.event;

import dev.dunnam.diceanchors.anchor.ConflictResolver;

/**
 * Published after a conflict between an incoming proposition and an existing
 * anchor is resolved.
 */
public class ConflictResolvedEvent extends AnchorLifecycleEvent {

    private final String existingAnchorId;
    private final ConflictResolver.Resolution resolution;

    public ConflictResolvedEvent(Object source, String contextId,
                                 String existingAnchorId,
                                 ConflictResolver.Resolution resolution) {
        super(source, contextId);
        this.existingAnchorId = existingAnchorId;
        this.resolution = resolution;
    }

    public String getExistingAnchorId() {
        return existingAnchorId;
    }

    public ConflictResolver.Resolution getResolution() {
        return resolution;
    }
}
