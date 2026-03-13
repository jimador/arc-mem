package dev.arcmem.core.memory.trust;

import dev.arcmem.core.persistence.PropositionNode;

/**
 * Context for trust evaluation of a single proposition.
 */
public record TrustContext(PropositionNode proposition, String contextId) {}
