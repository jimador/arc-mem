package dev.dunnam.diceanchors.anchor;

public interface DecayPolicy {

    /**
     * Calculate the new rank after applying decay based on elapsed time.
     *
     * @param anchor                  the anchor to decay
     * @param hoursSinceReinforcement hours elapsed since the anchor was last reinforced
     *
     * @return the new rank, clamped to [Anchor.MIN_RANK, Anchor.MAX_RANK]
     */
    int applyDecay(Anchor anchor, long hoursSinceReinforcement);
}
