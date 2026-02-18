package dev.dunnam.diceanchors.sim.assertions;

import dev.dunnam.diceanchors.sim.engine.AssertionResult;
import dev.dunnam.diceanchors.sim.engine.SimulationAssertion;
import dev.dunnam.diceanchors.sim.engine.SimulationResult;

import java.util.Map;

/**
 * Asserts the final anchor count falls within [min, max].
 */
public class AnchorCountAssertion implements SimulationAssertion {

    private final int min;
    private final int max;

    public AnchorCountAssertion(Map<String, Object> params) {
        this.min = intParam(params, "min", 0);
        this.max = intParam(params, "max", Integer.MAX_VALUE);
    }

    @Override
    public AssertionResult evaluate(SimulationResult result) {
        var count = result.finalAnchors().size();
        var passed = count >= min && count <= max;
        var details = "Anchor count: %d (expected [%d, %d])".formatted(count, min, max);
        return passed ? AssertionResult.pass("anchor-count", details)
                : AssertionResult.fail("anchor-count", details);
    }

    private static int intParam(Map<String, Object> params, String key, int defaultValue) {
        var val = params.get(key);
        return val instanceof Number n ? n.intValue() : defaultValue;
    }
}
