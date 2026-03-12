package dev.dunnam.diceanchors.sim.assertions;

import dev.dunnam.diceanchors.sim.engine.AssertionResult;
import dev.dunnam.diceanchors.sim.engine.SimulationAssertion;
import dev.dunnam.diceanchors.sim.engine.SimulationResult;

import java.util.Map;

/**
 * Asserts that at least {@code minAbove} anchors have rank strictly greater than {@code rankThreshold}.
 */
public class RankDistributionAssertion implements SimulationAssertion {

    private final int minAbove;
    private final int rankThreshold;

    public RankDistributionAssertion(Map<String, Object> params) {
        this.minAbove = intParam(params, "minAbove", 1);
        this.rankThreshold = intParam(params, "rankThreshold", 500);
    }

    @Override
    public AssertionResult evaluate(SimulationResult result) {
        var count = result.finalAnchors().stream()
                          .filter(a -> a.rank() > rankThreshold)
                          .count();
        var passed = count >= minAbove;
        var details = "%d anchors above rank %d (required >= %d)".formatted(count, rankThreshold, minAbove);
        return passed ? AssertionResult.pass("rank-distribution", details)
                : AssertionResult.fail("rank-distribution", details);
    }

    private static int intParam(Map<String, Object> params, String key, int defaultValue) {
        var val = params.get(key);
        return val instanceof Number n ? n.intValue() : defaultValue;
    }
}
