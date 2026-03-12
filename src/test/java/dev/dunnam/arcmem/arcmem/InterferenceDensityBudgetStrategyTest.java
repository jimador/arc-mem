package dev.dunnam.diceanchors.anchor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InterferenceDensityBudgetStrategy")
class InterferenceDensityBudgetStrategyTest {

    private static final double WARNING_THRESHOLD = 0.6;
    private static final double REDUCTION_THRESHOLD = 0.8;
    private static final double REDUCTION_FACTOR = 0.5;

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Anchor anchor(String id, int rank, Authority authority, boolean pinned) {
        return Anchor.withoutTrust(id, "text-" + id, rank, authority, pinned, 0.8, 0);
    }

    private static Anchor anchor(String id, int rank) {
        return anchor(id, rank, Authority.PROVISIONAL, false);
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

    private static ConflictIndex indexWithEdges(String... pairs) {
        var backing = new ConcurrentHashMap<String, Set<ConflictEntry>>();
        for (int i = 0; i < pairs.length; i += 2) {
            var a = pairs[i];
            var b = pairs[i + 1];
            var ab = new ConflictEntry(b, "t", Authority.PROVISIONAL, ConflictType.CONTRADICTION, 0.9, Instant.now());
            var ba = new ConflictEntry(a, "t", Authority.PROVISIONAL, ConflictType.CONTRADICTION, 0.9, Instant.now());
            backing.computeIfAbsent(a, k -> ConcurrentHashMap.newKeySet()).add(ab);
            backing.computeIfAbsent(b, k -> ConcurrentHashMap.newKeySet()).add(ba);
        }
        return new ConflictIndex() {
            @Override public Set<ConflictEntry> getConflicts(String id) {
                var e = backing.get(id); return e != null ? Set.copyOf(e) : Set.of();
            }
            @Override public void recordConflict(String id, ConflictEntry e) {}
            @Override public void removeConflicts(String id) {}
            @Override public void clear(String ctx) {}
            @Override public boolean hasConflicts(String id) { return backing.containsKey(id); }
            @Override public int size() { return backing.values().stream().mapToInt(Set::size).sum(); }
        };
    }

    private InterferenceDensityBudgetStrategy strategy(ConflictIndex index) {
        return new InterferenceDensityBudgetStrategy(
                new ConnectedComponentsCalculator(), index,
                WARNING_THRESHOLD, REDUCTION_THRESHOLD, REDUCTION_FACTOR);
    }

    // ── computeEffectiveBudget ────────────────────────────────────────────────

    @Nested
    @DisplayName("computeEffectiveBudget")
    class ComputeEffectiveBudget {

        @Test
        @DisplayName("low density (0.0) returns raw budget unchanged")
        void lowDensityReturnsRawBudget() {
            var anchors = List.of(anchor("a1", 500), anchor("a2", 400));
            var result = strategy(noOpIndex()).computeEffectiveBudget(anchors, 20);
            assertThat(result).isEqualTo(20);
        }

        @Test
        @DisplayName("density below reduction threshold returns raw budget")
        void densityBelowThresholdReturnsRawBudget() {
            // 2 anchors, 1 edge -> density = 1.0, but let's use 3 anchors with 1 edge -> density = 0.33
            var anchors = List.of(anchor("a1", 500), anchor("a2", 400), anchor("a3", 300));
            var index = indexWithEdges("a1", "a2"); // density = 1/3 = 0.33 < 0.8
            var result = strategy(index).computeEffectiveBudget(anchors, 20);
            assertThat(result).isEqualTo(20);
        }

        @Test
        @DisplayName("high density (1.0) reduces effective budget per formula")
        void highDensityReducesBudget() {
            // 2 anchors fully connected: density = 1.0 >= 0.8
            // effectiveBudget = max(1, floor(20 * (1.0 - 1.0 * 0.5))) = max(1, floor(10)) = 10
            var anchors = List.of(anchor("a1", 500), anchor("a2", 400));
            var index = indexWithEdges("a1", "a2");
            var result = strategy(index).computeEffectiveBudget(anchors, 20);
            assertThat(result).isEqualTo(10);
        }

        @Test
        @DisplayName("effective budget never exceeds raw budget")
        void effectiveBudgetNeverExceedsRaw() {
            var anchors = List.of(anchor("a1", 500));
            var result = strategy(noOpIndex()).computeEffectiveBudget(anchors, 20);
            assertThat(result).isLessThanOrEqualTo(20);
        }

        @Test
        @DisplayName("effective budget always at least 1")
        void effectiveBudgetAlwaysAtLeastOne() {
            // Even with density=1.0 and reductionFactor=0.5: floor(1 * (1 - 1*0.5)) = 0, but max(1,0) = 1
            var anchors = List.of(anchor("a1", 500), anchor("a2", 400));
            var index = indexWithEdges("a1", "a2");
            // raw=1: floor(1 * 0.5) = 0 -> max(1,0) = 1
            var result = strategy(index).computeEffectiveBudget(anchors, 1);
            assertThat(result).isGreaterThanOrEqualTo(1);
        }
    }

    // ── selectForEviction ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("selectForEviction")
    class SelectForEviction {

        @Test
        @DisplayName("no clusters falls back to global lowest-rank")
        void noClustersFallsBackToGlobalLowestRank() {
            var anchors = List.of(anchor("a1", 300), anchor("a2", 500), anchor("a3", 700));
            var evicted = strategy(noOpIndex()).selectForEviction(anchors, 1);
            assertThat(evicted).hasSize(1);
            assertThat(evicted.get(0).id()).isEqualTo("a1");
        }

        @Test
        @DisplayName("cluster-aware eviction selects from largest cluster")
        void clusterAwareEvictionSelectsFromLargestCluster() {
            // a1-a2-a3 form one cluster; a4 isolated
            // a1 is lowest rank in the cluster
            var a1 = anchor("a1", 100);
            var a2 = anchor("a2", 200);
            var a3 = anchor("a3", 300);
            var a4 = anchor("a4", 50); // isolated, would be chosen by count-based
            var anchors = List.of(a1, a2, a3, a4);
            var index = indexWithEdges("a1", "a2", "a2", "a3");

            var evicted = strategy(index).selectForEviction(anchors, 1);

            assertThat(evicted).hasSize(1);
            // a1 is lowest-rank in the cluster; a4 is isolated so not in a cluster
            assertThat(evicted.get(0).id()).isEqualTo("a1");
        }

        @Test
        @DisplayName("pinned anchors immune to cluster eviction")
        void pinnedAnchorsImmuneToClusterEviction() {
            var pinned = anchor("pinned", 100, Authority.PROVISIONAL, true);
            var evictable = anchor("evict", 200, Authority.PROVISIONAL, false);
            var anchors = List.of(pinned, evictable);
            var index = indexWithEdges("pinned", "evict");

            var evicted = strategy(index).selectForEviction(anchors, 1);

            assertThat(evicted).extracting(Anchor::id).doesNotContain("pinned");
        }

        @Test
        @DisplayName("CANON anchors immune to cluster eviction")
        void canonAnchorsImmuneToClusterEviction() {
            var canon = anchor("canon", 100, Authority.CANON, false);
            var evictable = anchor("evict", 200, Authority.PROVISIONAL, false);
            var anchors = List.of(canon, evictable);
            var index = indexWithEdges("canon", "evict");

            var evicted = strategy(index).selectForEviction(anchors, 1);

            assertThat(evicted).extracting(Anchor::id).doesNotContain("canon");
        }
    }
}
