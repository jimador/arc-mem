package dev.arcmem.core.memory.mutation;

public record MutationRequest(
        String unitId,
        String revisedText,
        MutationSource source,
        String requesterId
) {}
