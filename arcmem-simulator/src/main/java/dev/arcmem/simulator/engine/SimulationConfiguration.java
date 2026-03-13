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

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.core.persistence.PassThroughTieredRepository;
import dev.arcmem.core.persistence.TieredMemoryUnitRepository;
import dev.arcmem.simulator.config.ArcMemSimulatorProperties;
import org.drivine.manager.PersistenceManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ArcMemSimulatorProperties.class)
public class SimulationConfiguration {

    @Bean
    @ConditionalOnProperty(name = "arc-mem.run-history.store", havingValue = "MEMORY")
    RunHistoryStore memoryRunHistoryStore() {
        return new SimulationRunStore();
    }

    @Bean
    @ConditionalOnProperty(name = "arc-mem.run-history.store", havingValue = "NEO4J", matchIfMissing = true)
    RunHistoryStore neo4jRunHistoryStore(PersistenceManager persistenceManager, ObjectMapper objectMapper) {
        return new Neo4jRunHistoryStore(persistenceManager, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    TieredMemoryUnitRepository passThroughTieredRepository(ArcMemEngine engine, ArcMemProperties properties) {
        return new PassThroughTieredRepository(engine, properties);
    }

    @Bean
    SimulationTurnServices simulationTurnServices(
            SimulationExtractionService extractionService,
            MaintenanceStrategy maintenanceStrategy,
            ComplianceEnforcer complianceEnforcer,
            MemoryPressureGauge pressureGauge,
            LoggingPromptInjectionEnforcer injectionEnforcer) {
        return new SimulationTurnServices(
                extractionService, maintenanceStrategy, complianceEnforcer,
                pressureGauge, injectionEnforcer);
    }
}
