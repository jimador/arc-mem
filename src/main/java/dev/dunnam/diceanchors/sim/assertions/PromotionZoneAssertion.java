package dev.dunnam.diceanchors.sim.assertions;

import dev.dunnam.diceanchors.anchor.PromotionZone;
import dev.dunnam.diceanchors.sim.engine.AssertionResult;
import dev.dunnam.diceanchors.sim.engine.SimulationAssertion;
import dev.dunnam.diceanchors.sim.engine.SimulationResult;

import java.util.Map;

/**
 * Asserts that at least {@code minCount} anchors are in the specified {@link PromotionZone}.
 */
public class PromotionZoneAssertion implements SimulationAssertion {

    private final PromotionZone zone;
    private final int minCount;

    public PromotionZoneAssertion(Map<String, Object> params) {
        var zoneName = (String) params.getOrDefault("zone", "AUTO_PROMOTE");
        this.zone = PromotionZone.valueOf(zoneName);
        this.minCount = intParam(params, "minCount", 1);
    }

    @Override
    public AssertionResult evaluate(SimulationResult result) {
        var count = result.finalAnchors().stream()
                          .filter(a -> a.trustScore() != null)
                          .filter(a -> a.trustScore().promotionZone() == zone)
                          .count();
        var passed = count >= minCount;
        var details = "%d anchors in zone %s (required >= %d)".formatted(count, zone, minCount);
        return passed ? AssertionResult.pass("promotion-zone", details)
                : AssertionResult.fail("promotion-zone", details);
    }

    private static int intParam(Map<String, Object> params, String key, int defaultValue) {
        var val = params.get(key);
        return val instanceof Number n ? n.intValue() : defaultValue;
    }
}
