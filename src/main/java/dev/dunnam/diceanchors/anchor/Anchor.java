package dev.dunnam.diceanchors.anchor;

import org.jspecify.annotations.Nullable;

/**
 * Immutable in-memory view of a promoted proposition (anchor).
 * <p>
 * Invariant A1: rank is always in [MIN_RANK, MAX_RANK].
 * Invariant A2: authority is never null for an active anchor.
 * Invariant A3: pinned anchors are immune to rank-based eviction.
 * Invariant A4: trustScore may be null for anchors created before trust scoring is active.
 */
public record Anchor(
        String id,
        String text,
        int rank,
        Authority authority,
        boolean pinned,
        double confidence,
        int reinforcementCount,
        @Nullable TrustScore trustScore
) {
    public static final int MIN_RANK = 100;
    public static final int MAX_RANK = 900;

    public static int clampRank(int rank) {
        return Math.max(MIN_RANK, Math.min(MAX_RANK, rank));
    }

    /**
     * Creates an Anchor without a trust score.
     * Use this factory to minimize churn in code that does not participate
     * in trust evaluation.
     */
    public static Anchor withoutTrust(String id, String text, int rank, Authority authority,
                                      boolean pinned, double confidence, int reinforcementCount) {
        return new Anchor(id, text, rank, authority, pinned, confidence, reinforcementCount, null);
    }
}
