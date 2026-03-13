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

import java.util.List;
import java.util.Map;

/**
 * Asserts that the final unit texts contain all specified patterns (case-insensitive substring match).
 */
public class KgContextContainsAssertion implements SimulationAssertion {

    private final List<String> patterns;

    @SuppressWarnings("unchecked")
    public KgContextContainsAssertion(Map<String, Object> params) {
        var raw = params.get("patterns");
        this.patterns = raw instanceof List<?> list
                ? ((List<String>) list).stream().map(String::toLowerCase).toList()
                : List.of();
    }

    @Override
    public AssertionResult evaluate(SimulationResult result) {
        var unitTexts = result.finalUnits().stream()
                                .map(MemoryUnit::text)
                                .map(String::toLowerCase)
                                .toList();

        var missing = patterns.stream()
                              .filter(pattern -> unitTexts.stream().noneMatch(t -> t.contains(pattern)))
                              .toList();

        if (missing.isEmpty()) {
            return AssertionResult.pass("kg-context-contains",
                                        "All %d patterns found in unit context".formatted(patterns.size()));
        }

        return AssertionResult.fail("kg-context-contains",
                                    "Missing patterns: %s".formatted(missing));
    }
}
