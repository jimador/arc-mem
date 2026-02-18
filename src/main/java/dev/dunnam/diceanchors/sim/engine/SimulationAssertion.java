package dev.dunnam.diceanchors.sim.engine;

/**
 * SPI for simulation assertions that verify invariants after a run completes.
 * Each implementation evaluates a specific property of the simulation result.
 */
public interface SimulationAssertion {

    /**
     * Evaluate this assertion against a completed simulation.
     *
     * @param result the simulation result to check
     *
     * @return the assertion outcome with pass/fail and details
     */
    AssertionResult evaluate(SimulationResult result);
}
