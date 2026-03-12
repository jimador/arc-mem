package dev.dunnam.diceanchors.sim.assertions;

import dev.dunnam.diceanchors.sim.engine.AssertionResult;
import dev.dunnam.diceanchors.sim.engine.SimulationAssertion;
import dev.dunnam.diceanchors.sim.engine.SimulationResult;

import java.util.Map;

/**
 * Asserts all anchors with a non-null trust score have scores within [min, max].
 */
public class TrustScoreRangeAssertion implements SimulationAssertion {

    private final double min;
    private final double max;

    public TrustScoreRangeAssertion(Map<String, Object> params) {
        this.min = doubleParam(params, "min", 0.0);
        this.max = doubleParam(params, "max", 1.0);
    }

    @Override
    public AssertionResult evaluate(SimulationResult result) {
        var outOfRange = result.finalAnchors().stream()
                               .filter(a -> a.trustScore() != null)
                               .filter(a -> a.trustScore().score() < min || a.trustScore().score() > max)
                               .toList();

        if (outOfRange.isEmpty()) {
            return AssertionResult.pass("trust-score-range",
                                        "All trust scores within [%.2f, %.2f]".formatted(min, max));
        }

        var details = new StringBuilder("Trust scores out of range [%.2f, %.2f]:".formatted(min, max));
        for (var anchor : outOfRange) {
            details.append(" [%s=%.3f]".formatted(anchor.id(), anchor.trustScore().score()));
        }
        return AssertionResult.fail("trust-score-range", details.toString());
    }

    private static double doubleParam(Map<String, Object> params, String key, double defaultValue) {
        var val = params.get(key);
        return val instanceof Number n ? n.doubleValue() : defaultValue;
    }
}
