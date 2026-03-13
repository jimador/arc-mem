package dev.arcmem.simulator.engine;
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

import dev.arcmem.core.spi.llm.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;


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
