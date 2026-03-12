package dev.dunnam.diceanchors.anchor;

/**
 * Carries the identity and previous rank of an anchor that was evicted from the budget.
 * Returned by {@link dev.dunnam.diceanchors.persistence.AnchorRepository#evictLowestRanked}
 * so that {@code AnchorEngine.promote()} can publish an {@code Evicted} lifecycle event
 * for each evicted anchor.
 *
 * @param anchorId    the proposition node ID of the evicted anchor
 * @param previousRank the rank the anchor held before eviction
 */
public record EvictedAnchorInfo(String anchorId, int previousRank) {}
