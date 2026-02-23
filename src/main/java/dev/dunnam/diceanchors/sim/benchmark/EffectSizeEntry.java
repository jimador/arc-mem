package dev.dunnam.diceanchors.sim.benchmark;

/**
 * Cohen's d effect size for a single metric between two conditions.
 *
 * @param cohensD        the effect size value (positive means first condition higher)
 * @param interpretation standard label: "negligible", "small", "medium", or "large"
 * @param lowConfidence  true when the source data has high variance (CV > 0.5)
 */
public record EffectSizeEntry(
        double cohensD,
        String interpretation,
        boolean lowConfidence
) {
    /**
     * Returns the standard interpretation label for a given Cohen's d value.
     * Based on absolute value: |d| < 0.2 = negligible, 0.2-0.5 = small,
     * 0.5-0.8 = medium, >= 0.8 = large.
     */
    public static String interpret(double d) {
        var abs = Math.abs(d);
        if (abs < 0.2) return "negligible";
        if (abs < 0.5) return "small";
        if (abs < 0.8) return "medium";
        return "large";
    }
}
