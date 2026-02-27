package dev.dunnam.diceanchors.sim.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.drivine.manager.PersistenceManager;
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
}
