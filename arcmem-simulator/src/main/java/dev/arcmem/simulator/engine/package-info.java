/**
 * Simulation engine for adversarial and baseline unit drift testing.
 * <p>
 * {@link dev.arcmem.simulator.engine.SimulationService} is the entry point.
 * It orchestrates the full simulation lifecycle: phase state machine (SETUP → WARM_UP/ESTABLISH/ATTACK → COMPLETE),
 * seeds initial units, drives the turn loop, and delivers progress updates to the UI callback.
 * Each simulation run receives an isolated {@code contextId} to avoid cross-run contamination in Neo4j.
 * <p>
 * {@link dev.arcmem.simulator.scenario.SimulationTurnExecutor} executes a single turn:
 * assembles unit context, invokes the DM's LLM response, optionally evaluates drift (on adversarial ATTACK turns only),
 * and extracts new propositions from the response. When {@code parallelPostResponse} is enabled, drift evaluation and
 * proposition extraction run concurrently using {@link java.util.concurrent.StructuredTaskScope}.
 * <p>
 * Scenarios are defined in YAML ({@link dev.arcmem.simulator.scenario.SimulationScenario})
 * and loaded via {@link dev.arcmem.simulator.scenario.ScenarioLoader}.
 * Adversarial scenarios employ a {@link dev.arcmem.simulator.adversary.TieredEscalationStrategy}
 * to select attacks based on unit state and attack history, or scripted player messages for baseline runs.
 */
package dev.arcmem.simulator.engine;

import dev.arcmem.core.spi.llm.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;
