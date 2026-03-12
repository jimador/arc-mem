package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.persistence.PropositionNode;

/**
 * Context for trust evaluation of a single proposition.
 */
public record TrustContext(PropositionNode proposition, String contextId) {}
