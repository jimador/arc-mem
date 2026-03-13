package dev.arcmem.core.memory.conflict;
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConnectedComponentsCalculator")
class ConnectedComponentsCalculatorTest {

    private final ConnectedComponentsCalculator calculator = new ConnectedComponentsCalculator();

    @Nested
    @DisplayName("computeDensity")
    class ComputeDensity {

        @Test
        @DisplayName("zero memory units returns 0.0")
        void zeroUnitsReturnsDensityZero() {
            assertThat(calculator.computeDensity(List.of(), noOpIndex())).isEqualTo(0.0);
        }

        @Test
        @DisplayName("single memory unit returns 0.0")
        void singleUnitReturnsDensityZero() {
            var units = List.of(unit("a1"));
            assertThat(calculator.computeDensity(units, noOpIndex())).isEqualTo(0.0);
        }

        @Test
        @DisplayName("two memory units with conflict edge returns 1.0")
        void twoUnitsWithEdgeReturnsDensityOne() {
            var units = List.of(unit("a1"), unit("a2"));
            var index = indexWithEdges("a1", "a2");

            assertThat(calculator.computeDensity(units, index)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("two memory units without edge returns 0.0")
        void twoUnitsWithoutEdgeReturnsDensityZero() {
            var units = List.of(unit("a1"), unit("a2"));
            assertThat(calculator.computeDensity(units, noOpIndex())).isEqualTo(0.0);
        }

        @Test
        @DisplayName("fully connected graph of 3 returns 1.0")
        void fullyConnectedGraphReturnsDensityOne() {
            var units = List.of(unit("a1"), unit("a2"), unit("a3"));
            var index = indexWithEdges("a1", "a2", "a2", "a3", "a1", "a3");

            assertThat(calculator.computeDensity(units, index)).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("identifyClusters")
    class IdentifyClusters {

        @Test
        @DisplayName("zero memory units returns empty clusters")
        void zeroUnitsReturnsEmpty() {
            assertThat(calculator.identifyClusters(List.of(), noOpIndex())).isEmpty();
        }

        @Test
        @DisplayName("single isolated memory unit returns empty clusters")
        void singleIsolatedUnitReturnsEmpty() {
            assertThat(calculator.identifyClusters(List.of(unit("a1")), noOpIndex())).isEmpty();
        }

        @Test
        @DisplayName("two memory units with conflict edge form one cluster")
        void twoUnitsWithEdgeFormOneCluster() {
            var units = List.of(unit("a1"), unit("a2"));
            var index = indexWithEdges("a1", "a2");

            var clusters = calculator.identifyClusters(units, index);

            assertThat(clusters).hasSize(1);
            assertThat(clusters.get(0).unitIds()).containsExactlyInAnyOrder("a1", "a2");
        }

        @Test
        @DisplayName("two separate components form two clusters")
        void twoComponentsFormTwoClusters() {
            var units = List.of(unit("a1"), unit("a2"), unit("a3"), unit("a4"));
            var index = indexWithEdges("a1", "a2", "a3", "a4");

            var clusters = calculator.identifyClusters(units, index);

            assertThat(clusters).hasSize(2);
            var clusterIds = clusters.stream()
                    .map(c -> c.unitIds())
                    .toList();
            assertThat(clusterIds).anyMatch(s -> s.containsAll(Set.of("a1", "a2")));
            assertThat(clusterIds).anyMatch(s -> s.containsAll(Set.of("a3", "a4")));
        }

        @Test
        @DisplayName("isolated memory unit excluded from all clusters")
        void isolatedUnitExcludedFromClusters() {
            var units = List.of(unit("a1"), unit("a2"), unit("isolated"));
            var index = indexWithEdges("a1", "a2");

            var clusters = calculator.identifyClusters(units, index);

            assertThat(clusters).hasSize(1);
            assertThat(clusters.get(0).unitIds()).doesNotContain("isolated");
        }

        @Test
        @DisplayName("fully connected graph returns cluster with density 1.0")
        void fullyConnectedReturnsMaxDensity() {
            var units = List.of(unit("a1"), unit("a2"), unit("a3"));
            var index = indexWithEdges("a1", "a2", "a2", "a3", "a1", "a3");

            var clusters = calculator.identifyClusters(units, index);

            assertThat(clusters).hasSize(1);
            assertThat(clusters.get(0).internalDensity()).isEqualTo(1.0);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static MemoryUnit unit(String id) {
        return MemoryUnit.withoutTrust(id, "text-" + id, 500, Authority.PROVISIONAL, false, 0.8, 0);
    }

    private static ConflictIndex noOpIndex() {
        return new ConflictIndex() {
            @Override public Set<ConflictEntry> getConflicts(String unitId) { return Set.of(); }
            @Override public void recordConflict(String unitId, ConflictEntry entry) {}
            @Override public void removeConflicts(String unitId) {}
            @Override public void clear(String contextId) {}
            @Override public boolean hasConflicts(String unitId) { return false; }
            @Override public int size() { return 0; }
        };
    }

    /**
     * Builds a ConflictIndex from alternating (sourceId, targetId) pairs.
     * Edges are bidirectional: each pair registers conflicts in both directions.
     */
    private static ConflictIndex indexWithEdges(String... pairs) {
        var backing = new ConcurrentHashMap<String, Set<ConflictEntry>>();
        for (int i = 0; i < pairs.length; i += 2) {
            var a = pairs[i];
            var b = pairs[i + 1];
            var entryAtoB = new ConflictEntry(b, "text-" + b, Authority.PROVISIONAL,
                    ConflictType.CONTRADICTION, 0.9, Instant.now());
            var entryBtoA = new ConflictEntry(a, "text-" + a, Authority.PROVISIONAL,
                    ConflictType.CONTRADICTION, 0.9, Instant.now());
            backing.computeIfAbsent(a, k -> ConcurrentHashMap.newKeySet()).add(entryAtoB);
            backing.computeIfAbsent(b, k -> ConcurrentHashMap.newKeySet()).add(entryBtoA);
        }
        return new ConflictIndex() {
            @Override
            public Set<ConflictEntry> getConflicts(String unitId) {
                var entries = backing.get(unitId);
                return entries != null ? Set.copyOf(entries) : Set.of();
            }
            @Override public void recordConflict(String unitId, ConflictEntry entry) {}
            @Override public void removeConflicts(String unitId) {}
            @Override public void clear(String contextId) {}
            @Override public boolean hasConflicts(String unitId) { return backing.containsKey(unitId); }
            @Override public int size() { return backing.values().stream().mapToInt(Set::size).sum(); }
        };
    }
}
