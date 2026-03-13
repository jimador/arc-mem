package dev.arcmem.core.memory.budget;
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

@DisplayName("InterferenceDensityBudgetStrategy")
class InterferenceDensityBudgetStrategyTest {

    private static final double WARNING_THRESHOLD = 0.6;
    private static final double REDUCTION_THRESHOLD = 0.8;
    private static final double REDUCTION_FACTOR = 0.5;

    // ── helpers ──────────────────────────────────────────────────────────────

    private static MemoryUnit unit(String id, int rank, Authority authority, boolean pinned) {
        return MemoryUnit.withoutTrust(id, "text-" + id, rank, authority, pinned, 0.8, 0);
    }

    private static MemoryUnit unit(String id, int rank) {
        return unit(id, rank, Authority.PROVISIONAL, false);
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
            var units = List.of(unit("a1", 500), unit("a2", 400));
            var result = strategy(noOpIndex()).computeEffectiveBudget(units, 20);
            assertThat(result).isEqualTo(20);
        }

        @Test
        @DisplayName("density below reduction threshold returns raw budget")
        void densityBelowThresholdReturnsRawBudget() {
            // 2 units, 1 edge -> density = 1.0, but let's use 3 units with 1 edge -> density = 0.33
            var units = List.of(unit("a1", 500), unit("a2", 400), unit("a3", 300));
            var index = indexWithEdges("a1", "a2"); // density = 1/3 = 0.33 < 0.8
            var result = strategy(index).computeEffectiveBudget(units, 20);
            assertThat(result).isEqualTo(20);
        }

        @Test
        @DisplayName("high density (1.0) reduces effective budget per formula")
        void highDensityReducesBudget() {
            // 2 units fully connected: density = 1.0 >= 0.8
            // effectiveBudget = max(1, floor(20 * (1.0 - 1.0 * 0.5))) = max(1, floor(10)) = 10
            var units = List.of(unit("a1", 500), unit("a2", 400));
            var index = indexWithEdges("a1", "a2");
            var result = strategy(index).computeEffectiveBudget(units, 20);
            assertThat(result).isEqualTo(10);
        }

        @Test
        @DisplayName("effective budget never exceeds raw budget")
        void effectiveBudgetNeverExceedsRaw() {
            var units = List.of(unit("a1", 500));
            var result = strategy(noOpIndex()).computeEffectiveBudget(units, 20);
            assertThat(result).isLessThanOrEqualTo(20);
        }

        @Test
        @DisplayName("effective budget always at least 1")
        void effectiveBudgetAlwaysAtLeastOne() {
            // Even with density=1.0 and reductionFactor=0.5: floor(1 * (1 - 1*0.5)) = 0, but max(1,0) = 1
            var units = List.of(unit("a1", 500), unit("a2", 400));
            var index = indexWithEdges("a1", "a2");
            // raw=1: floor(1 * 0.5) = 0 -> max(1,0) = 1
            var result = strategy(index).computeEffectiveBudget(units, 1);
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
            var units = List.of(unit("a1", 300), unit("a2", 500), unit("a3", 700));
            var evicted = strategy(noOpIndex()).selectForEviction(units, 1);
            assertThat(evicted).hasSize(1);
            assertThat(evicted.get(0).id()).isEqualTo("a1");
        }

        @Test
        @DisplayName("cluster-aware eviction selects from largest cluster")
        void clusterAwareEvictionSelectsFromLargestCluster() {
            // a1-a2-a3 form one cluster; a4 isolated
            // a1 is lowest rank in the cluster
            var a1 = unit("a1", 100);
            var a2 = unit("a2", 200);
            var a3 = unit("a3", 300);
            var a4 = unit("a4", 50); // isolated, would be chosen by count-based
            var units = List.of(a1, a2, a3, a4);
            var index = indexWithEdges("a1", "a2", "a2", "a3");

            var evicted = strategy(index).selectForEviction(units, 1);

            assertThat(evicted).hasSize(1);
            // a1 is lowest-rank in the cluster; a4 is isolated so not in a cluster
            assertThat(evicted.get(0).id()).isEqualTo("a1");
        }

        @Test
        @DisplayName("pinned memory units immune to cluster eviction")
        void pinnedUnitsImmuneToClusterEviction() {
            var pinned = unit("pinned", 100, Authority.PROVISIONAL, true);
            var evictable = unit("evict", 200, Authority.PROVISIONAL, false);
            var units = List.of(pinned, evictable);
            var index = indexWithEdges("pinned", "evict");

            var evicted = strategy(index).selectForEviction(units, 1);

            assertThat(evicted).extracting(MemoryUnit::id).doesNotContain("pinned");
        }

        @Test
        @DisplayName("CANON memory units immune to cluster eviction")
        void canonUnitsImmuneToClusterEviction() {
            var canon = unit("canon", 100, Authority.CANON, false);
            var evictable = unit("evict", 200, Authority.PROVISIONAL, false);
            var units = List.of(canon, evictable);
            var index = indexWithEdges("canon", "evict");

            var evicted = strategy(index).selectForEviction(units, 1);

            assertThat(evicted).extracting(MemoryUnit::id).doesNotContain("canon");
        }
    }
}
