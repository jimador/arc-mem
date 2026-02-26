package dev.dunnam.diceanchors.sim.report;

/**
 * Effect size between two conditions for a single metric.
 *
 * @param conditionA     first condition name
 * @param conditionB     second condition name
 * @param metricKey      the metric being compared
 * @param cohensD        Cohen's d effect size value
 * @param interpretation "negligible", "small", "medium", or "large"
 * @param lowConfidence  true if sample count is low or variance is high
 */
public record EffectSizeSummary(
        String conditionA,
        String conditionB,
        String metricKey,
        double cohensD,
        String interpretation,
        boolean lowConfidence) {
}
