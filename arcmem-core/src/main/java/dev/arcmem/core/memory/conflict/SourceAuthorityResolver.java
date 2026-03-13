package dev.arcmem.core.memory.conflict;

@FunctionalInterface
public interface SourceAuthorityResolver {
    ResolutionContext.SourceAuthorityRelation compare(
            String incomingSourceId, String existingSourceId);
}
