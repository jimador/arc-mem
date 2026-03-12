package dev.dunnam.diceanchors.anchor;

/**
 * Strategy for determining how an anchor's rank and authority respond to reinforcement signals.
 * <p>
 * Implementations are invoked by the anchor engine whenever a reinforcement event
 * is applied to an active anchor. A single instance MAY be shared across multiple
 * concurrent reinforcement calls; therefore implementations MUST be thread-safe.
 */
public interface ReinforcementPolicy {

    /**
     * Calculate the rank boost to apply when an anchor is reinforced.
     *
     * @param anchor the anchor being reinforced (before the reinforcement is applied)
     * @return the number of rank points to add; MUST be {@code >= 0}
     */
    int calculateRankBoost(Anchor anchor);

    /**
     * Determine whether the anchor's authority should be upgraded after reinforcement.
     * CANON is never assigned automatically.
     * <p>
     * This method is called after the anchor's reinforcement count has been incremented,
     * so the count reflects the post-reinforcement state.
     *
     * @param anchor the anchor after its reinforcement count has been incremented
     * @return {@code true} if the authority should advance to the next level;
     *         MUST return {@code false} when {@code anchor.authority() == Authority.CANON}
     */
    boolean shouldUpgradeAuthority(Anchor anchor);

    /**
     * Static factory for the default threshold-based reinforcement policy.
     *
     * @return a {@link ThresholdReinforcementPolicy}
     */
    static ReinforcementPolicy threshold() {
        return new ThresholdReinforcementPolicy();
    }

    /**
     * Boosts rank by a fixed amount per reinforcement and upgrades authority
     * when reinforcement count crosses configured thresholds.
     * <p>
     * PROVISIONAL -&gt; UNRELIABLE at {@value ThresholdReinforcementPolicy#UNRELIABLE_THRESHOLD} reinforcements.<br>
     * UNRELIABLE -&gt; RELIABLE at {@value ThresholdReinforcementPolicy#RELIABLE_THRESHOLD} reinforcements.<br>
     * CANON is never assigned automatically.
     * <p>
     * This class is thread-safe: all fields are final constants.
     */
    class ThresholdReinforcementPolicy implements ReinforcementPolicy {

        static final int RANK_BOOST = 50;
        static final int UNRELIABLE_THRESHOLD = 3;
        static final int RELIABLE_THRESHOLD = 7;

        @Override
        public int calculateRankBoost(Anchor anchor) {
            return RANK_BOOST;
        }

        @Override
        public boolean shouldUpgradeAuthority(Anchor anchor) {
            if (anchor.authority() == Authority.CANON) {
                return false;
            }
            return (anchor.authority() == Authority.PROVISIONAL && anchor.reinforcementCount() >= UNRELIABLE_THRESHOLD)
                   || (anchor.authority() == Authority.UNRELIABLE && anchor.reinforcementCount() >= RELIABLE_THRESHOLD);
        }
    }

}
