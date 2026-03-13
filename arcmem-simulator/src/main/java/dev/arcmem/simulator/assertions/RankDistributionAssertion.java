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
 * Asserts that at least {@code minAbove} units have rank strictly greater than {@code rankThreshold}.
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
        var count = result.finalUnits().stream()
                          .filter(a -> a.rank() > rankThreshold)
                          .count();
        var passed = count >= minAbove;
        var details = "%d units above rank %d (required >= %d)".formatted(count, rankThreshold, minAbove);
        return passed ? AssertionResult.pass("rank-distribution", details)
                : AssertionResult.fail("rank-distribution", details);
    }

    private static int intParam(Map<String, Object> params, String key, int defaultValue) {
        var val = params.get(key);
        return val instanceof Number n ? n.intValue() : defaultValue;
    }
}
