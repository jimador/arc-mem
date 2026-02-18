package dev.dunnam.diceanchors.anchor.event;

/**
 * Published when an existing anchor's rank is boosted via reinforcement.
 */
public class AnchorReinforcedEvent extends AnchorLifecycleEvent {

    private final String anchorId;
    private final int previousRank;
    private final int newRank;
    private final int reinforcementCount;

    public AnchorReinforcedEvent(Object source, String contextId,
                                 String anchorId, int previousRank, int newRank,
                                 int reinforcementCount) {
        super(source, contextId);
        this.anchorId = anchorId;
        this.previousRank = previousRank;
        this.newRank = newRank;
        this.reinforcementCount = reinforcementCount;
    }

    public String getAnchorId() {
        return anchorId;
    }

    public int getPreviousRank() {
        return previousRank;
    }

    public int getNewRank() {
        return newRank;
    }

    public int getReinforcementCount() {
        return reinforcementCount;
    }
}
