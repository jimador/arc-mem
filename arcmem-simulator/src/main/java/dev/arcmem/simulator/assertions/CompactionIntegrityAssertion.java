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
 * Asserts that the final unit context contains all required fact texts (case-insensitive substring match).
 * Used to verify compaction does not lose critical information.
 */
public class CompactionIntegrityAssertion implements SimulationAssertion {

    private final List<String> requiredFacts;

    @SuppressWarnings("unchecked")
    public CompactionIntegrityAssertion(Map<String, Object> params) {
        var raw = params.get("requiredFacts");
        this.requiredFacts = raw instanceof List<?> list
                ? ((List<String>) list).stream().map(String::toLowerCase).toList()
                : List.of();
    }

    @Override
    public AssertionResult evaluate(SimulationResult result) {
        var unitTexts = result.finalUnits().stream()
                                .map(MemoryUnit::text)
                                .map(String::toLowerCase)
                                .toList();

        var missing = requiredFacts.stream()
                                   .filter(fact -> unitTexts.stream().noneMatch(t -> t.contains(fact)))
                                   .toList();

        if (missing.isEmpty()) {
            return AssertionResult.pass("compaction-integrity",
                                        "All %d required facts preserved".formatted(requiredFacts.size()));
        }

        return AssertionResult.fail("compaction-integrity",
                                    "Missing facts after compaction: %s".formatted(missing));
    }
}
