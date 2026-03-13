package dev.arcmem.core.persistence;

import org.jspecify.annotations.NonNull;

/**
 * Entity node derived from proposition mention data.
 */
public record EntityMentionNode(@NonNull String entityId,
                                @NonNull String label,
                                @NonNull String type,
                                int mentionCount,
                                int propositionCount) {}
