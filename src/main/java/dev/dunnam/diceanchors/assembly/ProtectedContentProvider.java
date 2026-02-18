package dev.dunnam.diceanchors.assembly;

import java.util.List;

/**
 * SPI for components that declare content as protected from compaction.
 * Implementations identify content that must survive summarization.
 */
public interface ProtectedContentProvider {

    /**
     * Return all content that should be protected from compaction in the given context.
     *
     * @param contextId the conversation or session context
     *
     * @return protected content entries, ordered by priority descending
     */
    List<ProtectedContent> getProtectedContent(String contextId);
}
