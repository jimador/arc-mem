package dev.arcmem.core.memory.mutation;

public sealed interface MutationDecision {
    record Allow() implements MutationDecision {}

    record Deny(String reason) implements MutationDecision {}

    record PendingApproval(String requestId) implements MutationDecision {}
}
