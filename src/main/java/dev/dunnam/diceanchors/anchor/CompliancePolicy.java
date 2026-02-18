package dev.dunnam.diceanchors.anchor;

/**
 * Policy that maps authority levels to compliance strengths.
 * Controls how strongly the LLM is instructed to respect anchors
 * at different authority tiers.
 */
public interface CompliancePolicy {
    ComplianceStrength getStrengthFor(Authority authority);
}
