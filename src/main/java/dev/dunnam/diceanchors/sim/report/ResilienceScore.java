package dev.dunnam.diceanchors.sim.report;

/**
 * Composite resilience metric distilling multi-metric experiment results into
 * a single headline number with breakdown.
 * <p>
 * Component weights: survival=0.40, drift=0.25, contradiction=0.20, strategy=0.15.
 * All values in range [0.0, 100.0].
 * <p>
 * Invariants:
 * <ul>
 *   <li>RS1: overall in [0.0, 100.0]</li>
 *   <li>RS2: all components in [0.0, 100.0]</li>
 *   <li>RS3: overall = survival*0.40 + drift*0.25 + contradiction*0.20 + strategy*0.15</li>
 * </ul>
 */
public record ResilienceScore(
        double overall,
        double survivalComponent,
        double driftResistanceComponent,
        double contradictionPenalty,
        double strategyResistanceComponent) {

    public String interpretation() {
        if (overall >= 90) {
            return "Excellent";
        }
        if (overall >= 75) {
            return "Good";
        }
        if (overall >= 50) {
            return "Moderate";
        }
        if (overall >= 25) {
            return "Weak";
        }
        return "Poor";
    }
}
