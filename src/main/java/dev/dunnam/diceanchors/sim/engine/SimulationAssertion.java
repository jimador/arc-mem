package dev.dunnam.diceanchors.sim.engine;

/**
 * SPI for simulation assertions that verify invariants after a run completes.
 * Each implementation evaluates a specific property of the simulation result.
 */
public interface SimulationAssertion {

    AssertionResult evaluate(SimulationResult result);
}
