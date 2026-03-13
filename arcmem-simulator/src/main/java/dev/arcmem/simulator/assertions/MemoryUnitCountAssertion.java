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
 * Asserts the final unit count falls within [min, max].
 */
public class MemoryUnitCountAssertion implements SimulationAssertion {

    private final int min;
    private final int max;

    public MemoryUnitCountAssertion(Map<String, Object> params) {
        this.min = intParam(params, "min", 0);
        this.max = intParam(params, "max", Integer.MAX_VALUE);
    }

    @Override
    public AssertionResult evaluate(SimulationResult result) {
        var count = result.finalUnits().size();
        var passed = count >= min && count <= max;
        var details = "MemoryUnit count: %d (expected [%d, %d])".formatted(count, min, max);
        return passed ? AssertionResult.pass("unit-count", details)
                : AssertionResult.fail("unit-count", details);
    }

    private static int intParam(Map<String, Object> params, String key, int defaultValue) {
        var val = params.get(key);
        return val instanceof Number n ? n.intValue() : defaultValue;
    }
}
