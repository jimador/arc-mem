package dev.arcmem.simulator.assertions;
import dev.arcmem.core.memory.budget.*;
import dev.arcmem.core.memory.canon.*;
import dev.arcmem.core.memory.conflict.*;
import dev.arcmem.core.memory.engine.*;
import dev.arcmem.core.memory.maintenance.*;
import dev.arcmem.core.memory.model.*;
import dev.arcmem.core.memory.mutation.*;
import dev.arcmem.core.memory.trust.*;
import dev.arcmem.core.assembly.budget.*;
import dev.arcmem.core.assembly.compaction.*;
import dev.arcmem.core.assembly.compliance.*;
import dev.arcmem.core.assembly.protection.*;
import dev.arcmem.core.assembly.retrieval.*;

import dev.arcmem.simulator.engine.*;
import dev.arcmem.simulator.scenario.*;

import dev.arcmem.simulator.engine.AssertionResult;
import dev.arcmem.simulator.engine.SimulationAssertion;
import dev.arcmem.simulator.engine.SimulationResult;

import java.util.Map;

/**
 * Asserts that at least {@code minCount} units are in the specified {@link PromotionZone}.
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
        var count = result.finalUnits().stream()
                          .filter(a -> a.trustScore() != null)
                          .filter(a -> a.trustScore().promotionZone() == zone)
                          .count();
        var passed = count >= minCount;
        var details = "%d units in zone %s (required >= %d)".formatted(count, zone, minCount);
        return passed ? AssertionResult.pass("promotion-zone", details)
                : AssertionResult.fail("promotion-zone", details);
    }

    private static int intParam(Map<String, Object> params, String key, int defaultValue) {
        var val = params.get(key);
        return val instanceof Number n ? n.intValue() : defaultValue;
    }
}
