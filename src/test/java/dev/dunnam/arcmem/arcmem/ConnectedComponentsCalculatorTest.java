package dev.dunnam.diceanchors.anchor;

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
        @DisplayName("zero anchors returns 0.0")
        void zeroAnchorsReturnsDensityZero() {
            assertThat(calculator.computeDensity(List.of(), noOpIndex())).isEqualTo(0.0);
        }

        @Test
        @DisplayName("single anchor returns 0.0")
        void singleAnchorReturnsDensityZero() {
            var anchors = List.of(anchor("a1"));
            assertThat(calculator.computeDensity(anchors, noOpIndex())).isEqualTo(0.0);
        }

        @Test
        @DisplayName("two anchors with conflict edge returns 1.0")
        void twoAnchorsWithEdgeReturnsDensityOne() {
            var anchors = List.of(anchor("a1"), anchor("a2"));
            var index = indexWithEdges("a1", "a2");

            assertThat(calculator.computeDensity(anchors, index)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("two anchors without edge returns 0.0")
        void twoAnchorsWithoutEdgeReturnsDensityZero() {
            var anchors = List.of(anchor("a1"), anchor("a2"));
            assertThat(calculator.computeDensity(anchors, noOpIndex())).isEqualTo(0.0);
        }

        @Test
        @DisplayName("fully connected graph of 3 returns 1.0")
        void fullyConnectedGraphReturnsDensityOne() {
            var anchors = List.of(anchor("a1"), anchor("a2"), anchor("a3"));
            var index = indexWithEdges("a1", "a2", "a2", "a3", "a1", "a3");

            assertThat(calculator.computeDensity(anchors, index)).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("identifyClusters")
    class IdentifyClusters {

        @Test
        @DisplayName("zero anchors returns empty clusters")
        void zeroAnchorsReturnsEmpty() {
            assertThat(calculator.identifyClusters(List.of(), noOpIndex())).isEmpty();
        }

        @Test
        @DisplayName("single isolated anchor returns empty clusters")
        void singleIsolatedAnchorReturnsEmpty() {
            assertThat(calculator.identifyClusters(List.of(anchor("a1")), noOpIndex())).isEmpty();
        }

        @Test
        @DisplayName("two anchors with conflict edge form one cluster")
        void twoAnchorsWithEdgeFormOneCluster() {
            var anchors = List.of(anchor("a1"), anchor("a2"));
            var index = indexWithEdges("a1", "a2");

            var clusters = calculator.identifyClusters(anchors, index);

            assertThat(clusters).hasSize(1);
            assertThat(clusters.get(0).anchorIds()).containsExactlyInAnyOrder("a1", "a2");
        }

        @Test
        @DisplayName("two separate components form two clusters")
        void twoComponentsFormTwoClusters() {
            var anchors = List.of(anchor("a1"), anchor("a2"), anchor("a3"), anchor("a4"));
            var index = indexWithEdges("a1", "a2", "a3", "a4");

            var clusters = calculator.identifyClusters(anchors, index);

            assertThat(clusters).hasSize(2);
            var clusterIds = clusters.stream()
                    .map(c -> c.anchorIds())
                    .toList();
            assertThat(clusterIds).anyMatch(s -> s.containsAll(Set.of("a1", "a2")));
            assertThat(clusterIds).anyMatch(s -> s.containsAll(Set.of("a3", "a4")));
        }

        @Test
        @DisplayName("isolated anchor excluded from all clusters")
        void isolatedAnchorExcludedFromClusters() {
            var anchors = List.of(anchor("a1"), anchor("a2"), anchor("isolated"));
            var index = indexWithEdges("a1", "a2");

            var clusters = calculator.identifyClusters(anchors, index);

            assertThat(clusters).hasSize(1);
            assertThat(clusters.get(0).anchorIds()).doesNotContain("isolated");
        }

        @Test
        @DisplayName("fully connected graph returns cluster with density 1.0")
        void fullyConnectedReturnsMaxDensity() {
            var anchors = List.of(anchor("a1"), anchor("a2"), anchor("a3"));
            var index = indexWithEdges("a1", "a2", "a2", "a3", "a1", "a3");

            var clusters = calculator.identifyClusters(anchors, index);

            assertThat(clusters).hasSize(1);
            assertThat(clusters.get(0).internalDensity()).isEqualTo(1.0);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Anchor anchor(String id) {
        return Anchor.withoutTrust(id, "text-" + id, 500, Authority.PROVISIONAL, false, 0.8, 0);
    }

    private static ConflictIndex noOpIndex() {
        return new ConflictIndex() {
            @Override public Set<ConflictEntry> getConflicts(String anchorId) { return Set.of(); }
            @Override public void recordConflict(String anchorId, ConflictEntry entry) {}
            @Override public void removeConflicts(String anchorId) {}
            @Override public void clear(String contextId) {}
            @Override public boolean hasConflicts(String anchorId) { return false; }
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
            public Set<ConflictEntry> getConflicts(String anchorId) {
                var entries = backing.get(anchorId);
                return entries != null ? Set.copyOf(entries) : Set.of();
            }
            @Override public void recordConflict(String anchorId, ConflictEntry entry) {}
            @Override public void removeConflicts(String anchorId) {}
            @Override public void clear(String contextId) {}
            @Override public boolean hasConflicts(String anchorId) { return backing.containsKey(anchorId); }
            @Override public int size() { return backing.values().stream().mapToInt(Set::size).sum(); }
        };
    }
}
