package dev.dunnam.diceanchors.assembly;

/**
 * A piece of content that should be protected from compaction.
 *
 * @param id       unique identifier for the content (e.g., anchor or proposition ID)
 * @param text     the protected text
 * @param priority higher values indicate greater protection priority
 * @param reason   human-readable explanation for why this content is protected
 */
public record ProtectedContent(
        String id,
        String text,
        int priority,
        String reason
) {}
