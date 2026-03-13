package dev.arcmem.core.memory.conflict;

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
     * Returns all known conflict entries for the given unit ID.
     * Returns an empty set if the unit has no recorded conflicts.
     */
    Set<ConflictEntry> getConflicts(String unitId);

    /**
     * Records a conflict entry for the given unit.
     * Implementations should be idempotent — recording the same entry twice has no effect.
     */
    void recordConflict(String unitId, ConflictEntry entry);

    /**
     * Removes all conflict entries associated with the given unit ID, including
     * references to this unit in other units' conflict sets.
     */
    void removeConflicts(String unitId);

    /**
     * Removes all conflict index entries for all units in the given context.
     */
    void clear(String contextId);

    /**
     * Returns true if the given unit has any recorded conflicts.
     */
    boolean hasConflicts(String unitId);

    /**
     * Returns the total number of conflict entries across all units.
     */
    int size();
}
