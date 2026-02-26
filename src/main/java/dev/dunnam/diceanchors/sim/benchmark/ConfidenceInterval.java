package dev.dunnam.diceanchors.sim.benchmark;

/**
 * A 95% confidence interval for a metric value.
 *
 * @param lower the lower bound of the interval
 * @param upper the upper bound of the interval
 */
public record ConfidenceInterval(double lower, double upper) {

    /**
     * Computes a 95% CI from descriptive statistics.
     * Formula: mean ± 1.96 × sampleStddev / √n
     *
     * @param mean         sample mean
     * @param sampleStddev sample standard deviation (N-1 denominator)
     * @param n            sample count
     *
     * @return the confidence interval, or (mean, mean) if n < 2
     */
    public static ConfidenceInterval of(double mean, double sampleStddev, int n) {
        if (n < 2 || Double.isNaN(mean) || Double.isNaN(sampleStddev)) {
            return new ConfidenceInterval(mean, mean);
        }
        var margin = 1.96 * sampleStddev / Math.sqrt(n);
        return new ConfidenceInterval(mean - margin, mean + margin);
    }
}
