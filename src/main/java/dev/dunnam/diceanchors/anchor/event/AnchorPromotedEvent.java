package dev.dunnam.diceanchors.anchor.event;

/**
 * Published when a proposition is promoted to anchor status.
 */
public class AnchorPromotedEvent extends AnchorLifecycleEvent {

    private final String propositionId;
    private final String anchorId;
    private final int initialRank;

    public AnchorPromotedEvent(Object source, String contextId,
                               String propositionId, String anchorId, int initialRank) {
        super(source, contextId);
        this.propositionId = propositionId;
        this.anchorId = anchorId;
        this.initialRank = initialRank;
    }

    public String getPropositionId() {
        return propositionId;
    }

    public String getAnchorId() {
        return anchorId;
    }

    public int getInitialRank() {
        return initialRank;
    }
}
