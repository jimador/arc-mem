package dev.dunnam.diceanchors.anchor;

/**
 * Applies exponential decay to anchor rank based on elapsed time.
 * Pinned anchors are immune to decay.
 * <p>
 * New rank = current rank * 0.5^(hours / halfLifeHours), clamped to [MIN_RANK, MAX_RANK].
 */
public class ExponentialDecayPolicy implements DecayPolicy {

    private final double halfLifeHours;

    public ExponentialDecayPolicy(double halfLifeHours) {
        this.halfLifeHours = halfLifeHours;
    }

    @Override
    public int applyDecay(Anchor anchor, long hoursSinceReinforcement) {
        if (anchor.pinned()) {
            return anchor.rank();
        }
        var decayFactor = Math.pow(0.5, hoursSinceReinforcement / halfLifeHours);
        return Anchor.clampRank((int) (anchor.rank() * decayFactor));
    }
}
