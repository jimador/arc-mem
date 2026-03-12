/**
 * Simulation engine for adversarial and baseline anchor drift testing.
 * <p>
 * {@link dev.dunnam.diceanchors.sim.engine.SimulationService} is the entry point.
 * It orchestrates the full simulation lifecycle: phase state machine (SETUP → WARM_UP/ESTABLISH/ATTACK → COMPLETE),
 * seeds initial anchors, drives the turn loop, and delivers progress updates to the UI callback.
 * Each simulation run receives an isolated {@code contextId} to avoid cross-run contamination in Neo4j.
 * <p>
 * {@link dev.dunnam.diceanchors.sim.engine.SimulationTurnExecutor} executes a single turn:
 * assembles anchor context, invokes the DM's LLM response, optionally evaluates drift (on adversarial ATTACK turns only),
 * and extracts new propositions from the response. When {@code parallelPostResponse} is enabled, drift evaluation and
 * proposition extraction run concurrently using {@link java.util.concurrent.StructuredTaskScope}.
 * <p>
 * Scenarios are defined in YAML ({@link dev.dunnam.diceanchors.sim.engine.SimulationScenario})
 * and loaded via {@link dev.dunnam.diceanchors.sim.engine.ScenarioLoader}.
 * Adversarial scenarios employ a {@link dev.dunnam.diceanchors.sim.engine.adversary.TieredEscalationStrategy}
 * to select attacks based on anchor state and attack history, or scripted player messages for baseline runs.
 */
package dev.dunnam.diceanchors.sim.engine;
