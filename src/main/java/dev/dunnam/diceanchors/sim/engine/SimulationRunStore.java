package dev.dunnam.diceanchors.sim.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory LRU store for completed simulation run records.
 * Evicts oldest entries when capacity is exceeded.
 * Thread-safe via synchronized access to the backing map.
 */
public class SimulationRunStore implements RunHistoryStore {

    private static final int MAX_ENTRIES = 50;

    private final Map<String, SimulationRunRecord> store = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, SimulationRunRecord> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    @Override
    public synchronized void save(SimulationRunRecord record) {
        store.put(record.runId(), record);
    }

    @Override
    public synchronized Optional<SimulationRunRecord> load(String runId) {
        return Optional.ofNullable(store.get(runId));
    }

    /**
     * @deprecated Use {@link #load(String)} instead.
     */
    @Deprecated(forRemoval = true)
    public Optional<SimulationRunRecord> get(String runId) {
        return load(runId);
    }

    @Override
    public synchronized List<SimulationRunRecord> list() {
        return List.copyOf(store.values());
    }

    @Override
    public synchronized List<SimulationRunRecord> listByScenario(String scenarioId) {
        return store.values().stream()
                    .filter(r -> r.scenarioId().equals(scenarioId))
                    .toList();
    }

    @Override
    public synchronized void delete(String runId) {
        store.remove(runId);
    }

    public synchronized int size() {
        return store.size();
    }
}
