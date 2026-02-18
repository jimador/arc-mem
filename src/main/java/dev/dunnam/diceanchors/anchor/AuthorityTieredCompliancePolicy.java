package dev.dunnam.diceanchors.anchor;

/**
 * Authority-tiered compliance policy — maps each authority
 * to an appropriate compliance strength.
 * <p>
 * CANON/RELIABLE → STRICT (MUST), UNRELIABLE → MODERATE (SHOULD),
 * PROVISIONAL → PERMISSIVE (MAY).
 */
public class AuthorityTieredCompliancePolicy implements CompliancePolicy {

    @Override
    public ComplianceStrength getStrengthFor(Authority authority) {
        return switch (authority) {
            case CANON, RELIABLE -> ComplianceStrength.STRICT;
            case UNRELIABLE -> ComplianceStrength.MODERATE;
            case PROVISIONAL -> ComplianceStrength.PERMISSIVE;
        };
    }
}
