package dev.dunnam.diceanchors.anchor;

/**
 * Flat compliance policy — all authorities treated as STRICT.
 * Preserves backward-compatible behavior.
 */
public class DefaultCompliancePolicy implements CompliancePolicy {

    @Override
    public ComplianceStrength getStrengthFor(Authority authority) {
        return ComplianceStrength.STRICT;
    }
}
