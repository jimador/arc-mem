package dev.dunnam.diceanchors.sim.engine;

import java.util.List;
import java.util.Optional;

/**
 * SPI for persisting simulation run records. Implementations MUST be thread-safe.
 */
public interface RunHistoryStore {

    void save(SimulationRunRecord record);

    Optional<SimulationRunRecord> load(String runId);

    List<SimulationRunRecord> list();

    List<SimulationRunRecord> listByScenario(String scenarioId);

    void delete(String runId);
}
