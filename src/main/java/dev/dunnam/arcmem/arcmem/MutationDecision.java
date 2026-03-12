package dev.dunnam.diceanchors.anchor;

public sealed interface MutationDecision {
    record Allow() implements MutationDecision {}
    record Deny(String reason) implements MutationDecision {}
    record PendingApproval(String requestId) implements MutationDecision {}
}
