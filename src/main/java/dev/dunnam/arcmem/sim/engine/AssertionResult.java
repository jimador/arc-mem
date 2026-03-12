package dev.dunnam.diceanchors.sim.engine;

/**
 * Result of evaluating a single {@link SimulationAssertion} against a simulation run.
 */
public record AssertionResult(
        String name,
        boolean passed,
        String details
) {
    public static AssertionResult pass(String name, String details) {
        return new AssertionResult(name, true, details);
    }

    public static AssertionResult fail(String name, String details) {
        return new AssertionResult(name, false, details);
    }
}
