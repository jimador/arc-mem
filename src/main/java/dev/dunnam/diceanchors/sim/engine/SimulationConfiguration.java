package dev.dunnam.diceanchors.sim.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.drivine.manager.PersistenceManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Conditional bean selection for {@link RunHistoryStore} implementations.
 * <p>
 * Selects between in-memory ({@link SimulationRunStore}) and Neo4j-backed
 * ({@link Neo4jRunHistoryStore}) based on the {@code dice-anchors.run-history.store} property.
 * Defaults to {@code memory}.
 */
@Configuration
public class SimulationConfiguration {

    @Bean
    @ConditionalOnProperty(name = "dice-anchors.run-history.store", havingValue = "memory", matchIfMissing = true)
    RunHistoryStore memoryRunHistoryStore() {
        return new SimulationRunStore();
    }

    @Bean
    @ConditionalOnProperty(name = "dice-anchors.run-history.store", havingValue = "neo4j")
    RunHistoryStore neo4jRunHistoryStore(PersistenceManager persistenceManager, ObjectMapper objectMapper) {
        return new Neo4jRunHistoryStore(persistenceManager, objectMapper);
    }
}
