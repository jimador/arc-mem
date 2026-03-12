package dev.dunnam.diceanchors.sim.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.MaintenanceStrategy;
import dev.dunnam.diceanchors.anchor.MemoryPressureGauge;
import dev.dunnam.diceanchors.assembly.ComplianceEnforcer;
import dev.dunnam.diceanchors.persistence.PassThroughTieredRepository;
import dev.dunnam.diceanchors.persistence.TieredAnchorRepository;
import org.drivine.manager.PersistenceManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SimulationConfiguration {

    @Bean
    @ConditionalOnProperty(name = "dice-anchors.run-history.store", havingValue = "MEMORY")
    RunHistoryStore memoryRunHistoryStore() {
        return new SimulationRunStore();
    }

    @Bean
    @ConditionalOnProperty(name = "dice-anchors.run-history.store", havingValue = "NEO4J", matchIfMissing = true)
    RunHistoryStore neo4jRunHistoryStore(PersistenceManager persistenceManager, ObjectMapper objectMapper) {
        return new Neo4jRunHistoryStore(persistenceManager, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    TieredAnchorRepository passThroughTieredRepository(AnchorEngine engine, DiceAnchorsProperties properties) {
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
