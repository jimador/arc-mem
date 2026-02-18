package dev.dunnam.diceanchors.anchor;

/**
 * Boosts rank by a fixed amount per reinforcement and upgrades authority
 * when reinforcement count crosses configured thresholds.
 * <p>
 * PROVISIONAL -> UNRELIABLE at {@value UNRELIABLE_THRESHOLD} reinforcements.
 * UNRELIABLE -> RELIABLE at {@value RELIABLE_THRESHOLD} reinforcements.
 * CANON is never assigned automatically.
 */
public class ThresholdReinforcementPolicy implements ReinforcementPolicy {

    private static final int RANK_BOOST = 50;
    private static final int UNRELIABLE_THRESHOLD = 3;
    private static final int RELIABLE_THRESHOLD = 7;

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
