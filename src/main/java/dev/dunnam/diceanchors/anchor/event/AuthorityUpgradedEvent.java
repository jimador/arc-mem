package dev.dunnam.diceanchors.anchor.event;

import dev.dunnam.diceanchors.anchor.Authority;

/**
 * Published when an anchor's authority level is upgraded after reinforcement.
 */
public class AuthorityUpgradedEvent extends AnchorLifecycleEvent {

    private final String anchorId;
    private final Authority previousAuthority;
    private final Authority newAuthority;
    private final int reinforcementCount;

    public AuthorityUpgradedEvent(Object source, String contextId,
                                  String anchorId, Authority previousAuthority,
                                  Authority newAuthority, int reinforcementCount) {
        super(source, contextId);
        this.anchorId = anchorId;
        this.previousAuthority = previousAuthority;
        this.newAuthority = newAuthority;
        this.reinforcementCount = reinforcementCount;
    }

    public String getAnchorId() {
        return anchorId;
    }

    public Authority getPreviousAuthority() {
        return previousAuthority;
    }

    public Authority getNewAuthority() {
        return newAuthority;
    }

    public int getReinforcementCount() {
        return reinforcementCount;
    }
}
