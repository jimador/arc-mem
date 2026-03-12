package dev.dunnam.diceanchors.assembly;

import java.util.List;

/**
 * SPI for components that declare content as protected from compaction.
 * Implementations identify content that must survive summarization.
 */
public interface ProtectedContentProvider {

    /**
     * Returns all content that should be protected from compaction in the given context,
     * ordered by priority descending.
     */
    List<ProtectedContent> getProtectedContent(String contextId);
}
