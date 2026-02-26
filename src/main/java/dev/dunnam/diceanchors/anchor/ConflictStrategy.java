package dev.dunnam.diceanchors.anchor;

/**
 * Top-level conflict detection strategy selection for configuration.
 * Maps to the conflict detector bean wired in {@link AnchorConfiguration}.
 */
public enum ConflictStrategy {
    LEXICAL,
    HYBRID,
    LLM
}
