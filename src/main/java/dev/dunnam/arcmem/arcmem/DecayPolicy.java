package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;

/**
 * Strategy interface for computing anchor rank decay over time.
 *
 * <h2>Thread safety</h2>
 * Implementations MUST be thread-safe. Multiple threads may call {@link #applyDecay}
 * and {@link #shouldDemoteAuthority} concurrently with different anchors.
 *
 * <h2>Error handling</h2>
 * Implementations MUST NOT throw from either method. On failure they MUST return
 * a safe default: the anchor's current rank for {@link #applyDecay}, and
 * {@code false} for {@link #shouldDemoteAuthority}.
 */
public interface DecayPolicy {

    /**
     * Calculate the new rank after applying decay based on elapsed time.
     * <p>
     * Contract:
     * <ul>
     *   <li>The returned rank MUST be clamped to [{@link Anchor#MIN_RANK}, {@link Anchor#MAX_RANK}].</li>
     *   <li>Pinned anchors MUST NOT have their rank reduced by decay.</li>
     *   <li>This method MUST NOT throw. On error, return {@code anchor.rank()} as a safe default.</li>
     * </ul>
     *
     * @param anchor                  the anchor to decay; never null
     * @param hoursSinceReinforcement hours elapsed since the anchor was last reinforced;
     *                                0 means no time has passed (result should equal current rank)
     * @return the new rank, clamped to [{@link Anchor#MIN_RANK}, {@link Anchor#MAX_RANK}]
     */
    int applyDecay(Anchor anchor, long hoursSinceReinforcement);

    /**
     * Calculate the new rank after applying tier-modulated decay.
     * <p>
     * The {@code tierMultiplier} scales the effective half-life: values &gt; 1.0 slow decay,
     * values &lt; 1.0 accelerate it. Clamped to a minimum of 0.01 to prevent division by zero.
     *
     * @param anchor                  the anchor to decay; never null
     * @param hoursSinceReinforcement hours elapsed since last reinforcement
     * @param tierMultiplier          multiplier from the anchor's memory tier
     * @return the new rank, clamped to [{@link Anchor#MIN_RANK}, {@link Anchor#MAX_RANK}]
     */
    default int applyDecay(Anchor anchor, long hoursSinceReinforcement, double tierMultiplier) {
        return applyDecay(anchor, hoursSinceReinforcement);
    }

    /**
     * Returns {@code true} when the anchor's decayed rank should trigger an authority demotion.
     * CANON anchors are exempt from automatic decay-based demotion (invariant A3b).
     * PROVISIONAL has no lower threshold.
     *
     * @param anchor  the anchor after rank decay has been applied
     * @param newRank the new rank after decay
     * @return true if the anchor should be demoted based on rank thresholds
     */
    boolean shouldDemoteAuthority(Anchor anchor, int newRank);

    /**
     * Static factory for exponential decay policy using default authority thresholds.
     *
     * @param halfLifeHours the halfLife of the decay
     * @return the decay policy
     */
    static DecayPolicy exponential(double halfLifeHours) {
        return new ExponentialDecayPolicy(halfLifeHours, null);
    }

    /**
     * Static factory for exponential decay policy with authority demotion support.
     *
     * @param halfLifeHours the halfLife of the decay
     * @param anchorConfig  anchor config supplying rank thresholds for demotion checks
     * @return the decay policy
     */
    static DecayPolicy exponential(double halfLifeHours, DiceAnchorsProperties.AnchorConfig anchorConfig) {
        return new ExponentialDecayPolicy(halfLifeHours, anchorConfig);
    }

    /**
     * Applies exponential decay to anchor rank based on elapsed time, modulated by the
     * anchor's {@code diceDecay} field.
     * <p>
     * Effective half-life formula: {@code effectiveHalfLife = baseHalfLife / max(diceDecay, 0.01)}.
     * <ul>
     *   <li>{@code diceDecay = 0.0} (permanent) — very large effective half-life, negligible decay.</li>
     *   <li>{@code diceDecay = 1.0} (standard) — standard rate, backward-compatible.</li>
     *   <li>{@code diceDecay > 1.0} (ephemeral) — shorter half-life, faster decay.</li>
     * </ul>
     * Pinned anchors are immune to decay.
     * <p>
     * New rank = current rank * 0.5^(hours / effectiveHalfLife), clamped to [MIN_RANK, MAX_RANK].
     */
    record ExponentialDecayPolicy(double halfLifeHours,
                                  DiceAnchorsProperties.AnchorConfig anchorConfig) implements DecayPolicy {

        @Override
        public int applyDecay(Anchor anchor, long hoursSinceReinforcement) {
            return applyDecay(anchor, hoursSinceReinforcement, 1.0);
        }

        @Override
        public int applyDecay(Anchor anchor, long hoursSinceReinforcement, double tierMultiplier) {
            if (anchor.pinned()) {
                return anchor.rank();
            }
            var effectiveHalfLife = halfLifeHours / Math.max(anchor.diceDecay(), 0.01) * Math.max(tierMultiplier, 0.01);
            var decayFactor = Math.pow(0.5, hoursSinceReinforcement / effectiveHalfLife);
            return Anchor.clampRank((int) (anchor.rank() * decayFactor));
        }

        @Override
        public boolean shouldDemoteAuthority(Anchor anchor, int newRank) {
            if (anchorConfig == null) {
                return false;
            }
            return switch (anchor.authority()) {
                case CANON, PROVISIONAL -> false;
                case RELIABLE -> newRank < anchorConfig.reliableRankThreshold();
                case UNRELIABLE -> newRank < anchorConfig.unreliableRankThreshold();
            };
        }
    }

}
