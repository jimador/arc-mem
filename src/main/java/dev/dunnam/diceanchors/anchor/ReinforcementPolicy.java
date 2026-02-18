package dev.dunnam.diceanchors.anchor;

public interface ReinforcementPolicy {

    /**
     * Calculate the rank boost to apply when an anchor is reinforced.
     *
     * @param anchor the anchor being reinforced (before the reinforcement is applied)
     *
     * @return the number of rank points to add
     */
    int calculateRankBoost(Anchor anchor);

    /**
     * Determine whether the anchor's authority should be upgraded after reinforcement.
     * CANON is never assigned automatically.
     *
     * @param anchor the anchor after its reinforcement count has been incremented
     *
     * @return true if the authority should advance to the next level
     */
    boolean shouldUpgradeAuthority(Anchor anchor);
}
