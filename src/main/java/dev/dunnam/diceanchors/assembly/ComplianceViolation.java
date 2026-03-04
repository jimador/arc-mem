package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Authority;

/**
 * A single anchor compliance violation detected during post-generation validation.
 *
 * @param anchorId        Neo4j node ID of the violated anchor
 * @param anchorText      proposition text of the violated anchor (for readability in logs/UI)
 * @param anchorAuthority authority level of the violated anchor; drives action escalation
 * @param description     human-readable description of what the violation is
 * @param confidence      validator confidence in this violation (0.0–1.0); LLM-sourced
 */
public record ComplianceViolation(
        String anchorId,
        String anchorText,
        Authority anchorAuthority,
        String description,
        double confidence
) {}
