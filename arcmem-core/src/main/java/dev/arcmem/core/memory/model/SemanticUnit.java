package dev.arcmem.core.memory.model;

/**
 * Extracted semantic content eligible for promotion to a {@link MemoryUnit}.
 * Abstracts over concrete extraction substrates (DICE propositions, claims,
 * events, etc.) so the promotion pipeline is representation-agnostic.
 */
public interface SemanticUnit {

    String id();

    String text();

    double confidence();

    boolean isPromotionCandidate();
}
