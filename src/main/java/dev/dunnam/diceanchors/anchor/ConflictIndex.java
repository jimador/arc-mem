package dev.dunnam.diceanchors.anchor;

import java.util.Set;

/**
 * Precomputed conflict adjacency structure enabling O(1) conflict lookup during promotion.
 * Inspired by STATIC's offline-index-for-online-lookup pattern where constraint relationships
 * are precomputed to enable efficient online enforcement.
 *
 * @see <a href="https://arxiv.org/abs/2406.02329">STATIC: Sparse Matrix Constrained Decoding</a>
 */
public interface ConflictIndex {

    /**
     * Returns all known conflict entries for the given anchor ID.
     * Returns an empty set if the anchor has no recorded conflicts.
     */
    Set<ConflictEntry> getConflicts(String anchorId);

    /**
     * Records a conflict entry for the given anchor.
     * Implementations should be idempotent — recording the same entry twice has no effect.
     */
    void recordConflict(String anchorId, ConflictEntry entry);

    /**
     * Removes all conflict entries associated with the given anchor ID, including
     * references to this anchor in other anchors' conflict sets.
     */
    void removeConflicts(String anchorId);

    /** Removes all conflict index entries for all anchors in the given context. */
    void clear(String contextId);

    /** Returns true if the given anchor has any recorded conflicts. */
    boolean hasConflicts(String anchorId);

    /** Returns the total number of conflict entries across all anchors. */
    int size();
}
