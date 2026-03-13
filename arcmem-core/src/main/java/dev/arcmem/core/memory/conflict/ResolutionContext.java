package dev.arcmem.core.memory.conflict;

import org.jspecify.annotations.Nullable;

public record ResolutionContext(
        @Nullable String incomingSourceId,
        SourceAuthorityRelation sourceRelation) {

    public enum SourceAuthorityRelation {
        SAME_SOURCE,
        INCOMING_OUTRANKS,
        EXISTING_OUTRANKS,
        UNKNOWN
    }

    public static final ResolutionContext NONE =
            new ResolutionContext(null, SourceAuthorityRelation.UNKNOWN);
}
