package dev.dunnam.diceanchors.sim.assertions;

import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.sim.engine.AssertionResult;
import dev.dunnam.diceanchors.sim.engine.SimulationAssertion;
import dev.dunnam.diceanchors.sim.engine.SimulationResult;

/**
 * Asserts no anchor has {@link Authority#CANON}, since CANON should never be auto-assigned.
 */
public class NoCanonAutoAssignedAssertion implements SimulationAssertion {

    @Override
    public AssertionResult evaluate(SimulationResult result) {
        var canonAnchors = result.finalAnchors().stream()
                                 .filter(a -> a.authority() == Authority.CANON)
                                 .toList();

        if (canonAnchors.isEmpty()) {
            return AssertionResult.pass("no-canon-auto-assigned", "No CANON anchors found");
        }

        var ids = canonAnchors.stream().map(a -> a.id()).toList();
        return AssertionResult.fail("no-canon-auto-assigned",
                                    "CANON auto-assigned to anchors: %s".formatted(ids));
    }
}
