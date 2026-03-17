package dev.arcmem.simulator.benchmark;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;

@Service
public class StatisticalTestRunner {

    /**
     * Mann-Whitney U test (two-tailed). Returns a p-value approximation using
     * the normal approximation for the U statistic with tie correction.
     *
     * @return p-value in [0, 1], or NaN if either sample has fewer than 2 observations
     */
    public double mannWhitneyU(double[] sample1, double[] sample2) {
        int n1 = sample1.length;
        int n2 = sample2.length;
        if (n1 < 2 || n2 < 2) {
            return Double.NaN;
        }

        record RankedValue(double value, int group) {}
        var combined = new ArrayList<RankedValue>(n1 + n2);
        for (var v : sample1) combined.add(new RankedValue(v, 1));
        for (var v : sample2) combined.add(new RankedValue(v, 2));
        combined.sort(Comparator.comparingDouble(RankedValue::value));

        int n = combined.size();
        var ranks = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j < n - 1 && combined.get(j + 1).value() == combined.get(i).value()) {
                j++;
            }
            double avgRank = (i + j) / 2.0 + 1.0;
            for (int k = i; k <= j; k++) {
                ranks[k] = avgRank;
            }
            i = j + 1;
        }

        double r1 = 0;
        for (int k = 0; k < n; k++) {
            if (combined.get(k).group() == 1) {
                r1 += ranks[k];
            }
        }

        double u1 = r1 - (double) n1 * (n1 + 1) / 2;
        double u2 = (double) n1 * n2 - u1;
        double u = Math.min(u1, u2);

        double mu = (double) n1 * n2 / 2;
        double sigma = Math.sqrt((double) n1 * n2 * (n1 + n2 + 1) / 12);
        if (sigma == 0) {
            return 1.0;
        }
        double z = Math.abs((u - mu) / sigma);
        double pValue = 2 * (1 - normalCdf(z));
        return Math.min(pValue, 1.0);
    }

    public double[] benjaminiHochberg(double[] pValues) {
        int m = pValues.length;
        if (m == 0) return new double[0];

        record IndexedP(int index, double pValue) {}
        var sorted = new ArrayList<IndexedP>(m);
        for (int k = 0; k < m; k++) {
            sorted.add(new IndexedP(k, pValues[k]));
        }
        sorted.sort(Comparator.comparingDouble(IndexedP::pValue));

        var corrected = new double[m];
        double minSoFar = 1.0;
        for (int k = m - 1; k >= 0; k--) {
            var entry = sorted.get(k);
            int rank = k + 1;
            double adjusted = entry.pValue() * m / rank;
            adjusted = Math.min(adjusted, minSoFar);
            adjusted = Math.min(adjusted, 1.0);
            corrected[entry.index()] = adjusted;
            minSoFar = adjusted;
        }
        return corrected;
    }

    public static String significanceLabel(double pValue) {
        if (Double.isNaN(pValue)) return "";
        if (pValue < 0.001) return "***";
        if (pValue < 0.01) return "**";
        if (pValue < 0.05) return "*";
        return "ns";
    }

    private static double normalCdf(double z) {
        if (z < -8) return 0.0;
        if (z > 8) return 1.0;
        double t = 1.0 / (1.0 + 0.2316419 * Math.abs(z));
        double d = 0.3989422804014327;
        double p = d * Math.exp(-z * z / 2.0)
                * (t * (0.319381530 + t * (-0.356563782 + t * (1.781477937
                + t * (-1.821255978 + t * 1.330274429)))));
        return z > 0 ? 1.0 - p : p;
    }
}
