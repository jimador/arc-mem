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

/**
 * Asserts no unit has {@link Authority#CANON}, since CANON should never be auto-assigned.
 */
public class NoCanonAutoAssignedAssertion implements SimulationAssertion {

    @Override
    public AssertionResult evaluate(SimulationResult result) {
        var canonUnits = result.finalUnits().stream()
                                 .filter(a -> a.authority() == Authority.CANON)
                                 .toList();

        if (canonUnits.isEmpty()) {
            return AssertionResult.pass("no-canon-auto-assigned", "No CANON units found");
        }

        var ids = canonUnits.stream().map(a -> a.id()).toList();
        return AssertionResult.fail("no-canon-auto-assigned",
                                    "CANON auto-assigned to units: %s".formatted(ids));
    }
}
