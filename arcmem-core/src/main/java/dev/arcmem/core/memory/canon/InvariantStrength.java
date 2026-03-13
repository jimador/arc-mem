package dev.arcmem.core.memory.canon;

/**
 * RFC 2119 strength level for operator invariant rules.
 * MUST violations block the attempted action; SHOULD violations warn and allow.
 */
public enum InvariantStrength {
    MUST,
    SHOULD
}
