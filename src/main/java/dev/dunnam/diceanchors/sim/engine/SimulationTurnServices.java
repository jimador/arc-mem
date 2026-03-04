package dev.dunnam.diceanchors.sim.engine;

import dev.dunnam.diceanchors.anchor.MaintenanceStrategy;
import dev.dunnam.diceanchors.anchor.MemoryPressureGauge;
import dev.dunnam.diceanchors.assembly.ComplianceEnforcer;

/**
 * Facade grouping the pipeline-phase services added to the simulation turn executor.
 * Reduces constructor arity by bundling the four pipeline-extension dependencies.
 */
public record SimulationTurnServices(
        SimulationExtractionService extractionService,
        MaintenanceStrategy maintenanceStrategy,
        ComplianceEnforcer complianceEnforcer,
        MemoryPressureGauge pressureGauge,
        LoggingPromptInjectionEnforcer injectionEnforcer
) {}
