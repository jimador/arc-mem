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
import dev.arcmem.simulator.engine.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimulationRunStore (in-memory RunHistoryStore)")
class InMemoryRunHistoryStoreTest {

    private SimulationRunStore store;

    @BeforeEach
    void setUp() {
        store = new SimulationRunStore();
    }

    private SimulationRunRecord record(String runId, String scenarioId) {
        return new SimulationRunRecord(
                runId, scenarioId, Instant.now(), Instant.now(),
                List.of(), 0, List.of(), true, 0, null, null
        );
    }

    @Nested
    @DisplayName("save and load")
    class SaveAndLoad {

        @Test
        @DisplayName("round-trips a saved record")
        void saveAndLoadRoundTrip() {
            var rec = record("run-1", "scenario-a");
            store.save(rec);

            var loaded = store.load("run-1");
            assertThat(loaded).isPresent().contains(rec);
        }

        @Test
        @DisplayName("returns empty for non-existent run")
        void loadNonExistentReturnsEmpty() {
            assertThat(store.load("no-such-run")).isEmpty();
        }
    }

    @Nested
    @DisplayName("list")
    class ListTests {

        @Test
        @DisplayName("returns all saved records")
        void listReturnsAllSaved() {
            store.save(record("run-1", "scenario-a"));
            store.save(record("run-2", "scenario-b"));
            store.save(record("run-3", "scenario-a"));

            assertThat(store.list()).hasSize(3);
        }

        @Test
        @DisplayName("returns empty list when store is empty")
        void listEmptyStore() {
            assertThat(store.list()).isEmpty();
        }
    }

    @Nested
    @DisplayName("listByScenario")
    class ListByScenarioTests {

        @Test
        @DisplayName("filters records by scenario ID")
        void filtersCorrectly() {
            store.save(record("run-1", "scenario-a"));
            store.save(record("run-2", "scenario-b"));
            store.save(record("run-3", "scenario-a"));

            var filtered = store.listByScenario("scenario-a");
            assertThat(filtered).hasSize(2);
            assertThat(filtered).extracting(SimulationRunRecord::scenarioId)
                    .containsOnly("scenario-a");
        }

        @Test
        @DisplayName("returns empty list when no records match")
        void noMatchReturnsEmpty() {
            store.save(record("run-1", "scenario-a"));

            assertThat(store.listByScenario("scenario-z")).isEmpty();
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("removes a saved record")
        void deleteRemovesRecord() {
            store.save(record("run-1", "scenario-a"));
            store.delete("run-1");

            assertThat(store.load("run-1")).isEmpty();
            assertThat(store.size()).isZero();
        }

        @Test
        @DisplayName("delete of non-existent run is a no-op")
        void deleteNonExistentIsNoOp() {
            store.save(record("run-1", "scenario-a"));
            store.delete("no-such-run");

            assertThat(store.size()).isEqualTo(1);
        }
    }
}
