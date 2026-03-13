package dev.arcmem.core.memory.mutation;

public interface MemoryUnitMutationStrategy {

    MutationDecision evaluate(MutationRequest request);
}
