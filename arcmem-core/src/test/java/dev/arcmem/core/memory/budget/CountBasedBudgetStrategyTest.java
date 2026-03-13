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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CountBasedBudgetStrategy")
class CountBasedBudgetStrategyTest {

    private final CountBasedBudgetStrategy strategy = new CountBasedBudgetStrategy();

    @Nested
    @DisplayName("computeEffectiveBudget")
    class ComputeEffectiveBudget {

        @Test
        @DisplayName("returns raw budget unchanged")
        void returnsRawBudget() {
            var units = List.of(unit("a1", 500, Authority.PROVISIONAL, false));
            assertThat(strategy.computeEffectiveBudget(units, 20)).isEqualTo(20);
        }

        @Test
        @DisplayName("returns raw budget for empty memory unit list")
        void returnsRawBudgetForEmpty() {
            assertThat(strategy.computeEffectiveBudget(List.of(), 10)).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("selectForEviction")
    class SelectForEviction {

        @Test
        @DisplayName("selects lowest-ranked non-pinned non-CANON up to excess count")
        void selectsLowestRanked() {
            var high = unit("high", 800, Authority.RELIABLE, false);
            var mid = unit("mid", 500, Authority.PROVISIONAL, false);
            var low = unit("low", 200, Authority.PROVISIONAL, false);

            var evicted = strategy.selectForEviction(List.of(high, mid, low), 1);

            assertThat(evicted).hasSize(1);
            assertThat(evicted.get(0).id()).isEqualTo("low");
        }

        @Test
        @DisplayName("excludes pinned memory units from eviction")
        void excludesPinned() {
            var pinned = unit("pinned", 100, Authority.PROVISIONAL, true);
            var evictable = unit("evict", 200, Authority.PROVISIONAL, false);

            var evicted = strategy.selectForEviction(List.of(pinned, evictable), 2);

            assertThat(evicted).extracting(MemoryUnit::id).containsOnly("evict");
        }

        @Test
        @DisplayName("excludes CANON memory units from eviction")
        void excludesCanon() {
            var canon = unit("canon", 100, Authority.CANON, false);
            var evictable = unit("evict", 200, Authority.PROVISIONAL, false);

            var evicted = strategy.selectForEviction(List.of(canon, evictable), 2);

            assertThat(evicted).extracting(MemoryUnit::id).containsOnly("evict");
        }

        @Test
        @DisplayName("empty memory unit list returns empty eviction list")
        void emptyListReturnsEmpty() {
            assertThat(strategy.selectForEviction(List.of(), 3)).isEmpty();
        }

        @Test
        @DisplayName("selects multiple memory units when excess > 1, lowest activation score first")
        void selectsMultipleLowestRanked() {
            var a = unit("a", 300, Authority.PROVISIONAL, false);
            var b = unit("b", 400, Authority.PROVISIONAL, false);
            var c = unit("c", 700, Authority.RELIABLE, false);

            var evicted = strategy.selectForEviction(List.of(a, b, c), 2);

            assertThat(evicted).hasSize(2);
            assertThat(evicted.get(0).id()).isEqualTo("a");
            assertThat(evicted.get(1).id()).isEqualTo("b");
        }
    }

    private static MemoryUnit unit(String id, int rank, Authority authority, boolean pinned) {
        return MemoryUnit.withoutTrust(id, "text-" + id, rank, authority, pinned, 0.8, 0);
    }
}
