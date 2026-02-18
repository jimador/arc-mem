package dev.dunnam.diceanchors.anchor.event;

/**
 * Published when an anchor is archived (deactivated).
 */
public class AnchorArchivedEvent extends AnchorLifecycleEvent {

    private final String anchorId;
    private final ArchiveReason reason;

    public AnchorArchivedEvent(Object source, String contextId,
                               String anchorId, ArchiveReason reason) {
        super(source, contextId);
        this.anchorId = anchorId;
        this.reason = reason;
    }

    public String getAnchorId() {
        return anchorId;
    }

    public ArchiveReason getReason() {
        return reason;
    }
}
