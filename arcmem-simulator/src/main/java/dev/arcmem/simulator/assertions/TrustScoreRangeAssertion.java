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
 * Asserts all units with a non-null trust score have scores within [min, max].
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
        var outOfRange = result.finalUnits().stream()
                               .filter(a -> a.trustScore() != null)
                               .filter(a -> a.trustScore().score() < min || a.trustScore().score() > max)
                               .toList();

        if (outOfRange.isEmpty()) {
            return AssertionResult.pass("trust-score-range",
                                        "All trust scores within [%.2f, %.2f]".formatted(min, max));
        }

        var details = new StringBuilder("Trust scores out of range [%.2f, %.2f]:".formatted(min, max));
        for (var unit : outOfRange) {
            details.append(" [%s=%.3f]".formatted(unit.id(), unit.trustScore().score()));
        }
        return AssertionResult.fail("trust-score-range", details.toString());
    }

    private static double doubleParam(Map<String, Object> params, String key, double defaultValue) {
        var val = params.get(key);
        return val instanceof Number n ? n.doubleValue() : defaultValue;
    }
}
