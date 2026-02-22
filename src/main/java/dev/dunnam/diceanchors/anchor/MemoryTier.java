package dev.dunnam.diceanchors.anchor;

/**
 * Memory tier classification for anchors, reflecting recency and reinforcement freshness.
 * <p>
 * Tiers are derived from the anchor's current rank using configurable thresholds.
 * Ordered {@code COLD < WARM < HOT} so that {@link #compareTo} works for sort operations.
 *
 * <h2>Tier semantics</h2>
 * <ul>
 *   <li><strong>HOT</strong> — actively reinforced, high recency. Decay is slower (protected window).</li>
 *   <li><strong>WARM</strong> — established but not recently reinforced. Baseline decay rate.</li>
 *   <li><strong>COLD</strong> — decayed, approaching eviction threshold. Decay is faster (accelerated cleanup).</li>
 * </ul>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li><strong>T1</strong>: tier is always consistent with current rank and configured thresholds
 *       after any rank-modifying operation.</li>
 *   <li><strong>T2</strong>: pinned anchors have their tier computed normally from rank.
 *       Pinning affects eviction immunity, not tier classification.</li>
 *   <li><strong>T3</strong>: CANON authority anchors have their tier computed normally from rank.
 *       Authority and tier are orthogonal dimensions.</li>
 * </ul>
 */
public enum MemoryTier {
    COLD,
    WARM,
    HOT;

    /**
     * Compute the memory tier for a given rank based on configured thresholds.
     *
     * @param rank          the anchor's current rank
     * @param hotThreshold  rank at or above which the anchor is HOT
     * @param warmThreshold rank at or above which (but below hotThreshold) the anchor is WARM
     * @return the computed memory tier
     */
    public static MemoryTier fromRank(int rank, int hotThreshold, int warmThreshold) {
        if (rank >= hotThreshold) {
            return HOT;
        }
        if (rank >= warmThreshold) {
            return WARM;
        }
        return COLD;
    }
}
