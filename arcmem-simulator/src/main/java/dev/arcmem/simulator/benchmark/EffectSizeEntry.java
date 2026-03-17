package dev.arcmem.simulator.benchmark;

/**
 * Cohen's d effect size for a single metric between two conditions,
 * with optional BH-corrected p-value and significance annotation.
 *
 * @param cohensD        the effect size value (positive means first condition higher)
 * @param interpretation standard label: "negligible", "small", "medium", or "large"
 * @param lowConfidence  true when the source data has high variance (CV > 0.5)
 * @param pValue         BH-corrected p-value from Mann-Whitney U test; NaN if not computed
 * @param significance   significance label: "***", "**", "*", "ns", or "" if not computed
 */
public record EffectSizeEntry(
        double cohensD,
        String interpretation,
        boolean lowConfidence,
        double pValue,
        String significance
) {
    public EffectSizeEntry(double cohensD, String interpretation, boolean lowConfidence) {
        this(cohensD, interpretation, lowConfidence, Double.NaN, "");
    }

    /**
     * Returns the standard interpretation label for a given Cohen's d value.
     * Based on absolute value: |d| < 0.2 = negligible, 0.2-0.5 = small,
     * 0.5-0.8 = medium, >= 0.8 = large.
     */
    public static String interpret(double d) {
        var abs = Math.abs(d);
        if (abs < 0.2) {
            return "negligible";
        }
        if (abs < 0.5) {
            return "small";
        }
        if (abs < 0.8) {
            return "medium";
        }
        return "large";
    }
}
