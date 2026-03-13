package dev.arcmem.core.memory.canon;

/**
 * Lifecycle actions that may be constrained by operator invariant rules.
 */
public enum ProposedAction {
    ARCHIVE, EVICT, DEMOTE, AUTHORITY_CHANGE
}
