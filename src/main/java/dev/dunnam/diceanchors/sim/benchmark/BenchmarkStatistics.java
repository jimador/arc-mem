package dev.dunnam.diceanchors.sim.benchmark;

/**
 * Descriptive statistics for a single scoring metric across multiple benchmark runs.
 *
 * @param mean        arithmetic mean of the sample
 * @param stddev      population standard deviation (N, not N-1)
 * @param min         minimum observed value
 * @param max         maximum observed value
 * @param median      50th percentile
 * @param p95         95th percentile (linear interpolation)
 * @param sampleCount number of data points
 */
public record BenchmarkStatistics(
        double mean,
        double stddev,
        double min,
        double max,
        double median,
        double p95,
        int sampleCount
) {
    /**
     * Coefficient of variation (stddev / mean). Returns 0.0 when mean is zero.
     */
    public double coefficientOfVariation() {
        return mean == 0.0 ? 0.0 : Math.abs(stddev / mean);
    }

    /**
     * Returns true when the coefficient of variation exceeds 0.5,
     * indicating high variance relative to the mean.
     */
    public boolean isHighVariance() {
        return coefficientOfVariation() > 0.5;
    }
}
