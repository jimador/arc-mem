package dev.dunnam.diceanchors.sim.assertions;

import dev.dunnam.diceanchors.sim.engine.AssertionResult;
import dev.dunnam.diceanchors.sim.engine.SimulationAssertion;
import dev.dunnam.diceanchors.sim.engine.SimulationResult;

/**
 * Asserts that no anchors remain at the end of the simulation.
 */
public class KgContextEmptyAssertion implements SimulationAssertion {

    @Override
    public AssertionResult evaluate(SimulationResult result) {
        var count = result.finalAnchors().size();
        return count == 0
                ? AssertionResult.pass("kg-context-empty", "No anchors remain")
                : AssertionResult.fail("kg-context-empty", "%d anchors still present".formatted(count));
    }
}
