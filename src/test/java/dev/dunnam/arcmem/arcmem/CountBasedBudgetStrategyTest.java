package dev.dunnam.diceanchors.anchor;

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
            var anchors = List.of(anchor("a1", 500, Authority.PROVISIONAL, false));
            assertThat(strategy.computeEffectiveBudget(anchors, 20)).isEqualTo(20);
        }

        @Test
        @DisplayName("returns raw budget for empty anchor list")
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
            var high = anchor("high", 800, Authority.RELIABLE, false);
            var mid = anchor("mid", 500, Authority.PROVISIONAL, false);
            var low = anchor("low", 200, Authority.PROVISIONAL, false);

            var evicted = strategy.selectForEviction(List.of(high, mid, low), 1);

            assertThat(evicted).hasSize(1);
            assertThat(evicted.get(0).id()).isEqualTo("low");
        }

        @Test
        @DisplayName("excludes pinned anchors from eviction")
        void excludesPinned() {
            var pinned = anchor("pinned", 100, Authority.PROVISIONAL, true);
            var evictable = anchor("evict", 200, Authority.PROVISIONAL, false);

            var evicted = strategy.selectForEviction(List.of(pinned, evictable), 2);

            assertThat(evicted).extracting(Anchor::id).containsOnly("evict");
        }

        @Test
        @DisplayName("excludes CANON anchors from eviction")
        void excludesCanon() {
            var canon = anchor("canon", 100, Authority.CANON, false);
            var evictable = anchor("evict", 200, Authority.PROVISIONAL, false);

            var evicted = strategy.selectForEviction(List.of(canon, evictable), 2);

            assertThat(evicted).extracting(Anchor::id).containsOnly("evict");
        }

        @Test
        @DisplayName("empty anchor list returns empty eviction list")
        void emptyListReturnsEmpty() {
            assertThat(strategy.selectForEviction(List.of(), 3)).isEmpty();
        }

        @Test
        @DisplayName("selects multiple anchors when excess > 1, lowest rank first")
        void selectsMultipleLowestRanked() {
            var a = anchor("a", 300, Authority.PROVISIONAL, false);
            var b = anchor("b", 400, Authority.PROVISIONAL, false);
            var c = anchor("c", 700, Authority.RELIABLE, false);

            var evicted = strategy.selectForEviction(List.of(a, b, c), 2);

            assertThat(evicted).hasSize(2);
            assertThat(evicted.get(0).id()).isEqualTo("a");
            assertThat(evicted.get(1).id()).isEqualTo("b");
        }
    }

    private static Anchor anchor(String id, int rank, Authority authority, boolean pinned) {
        return Anchor.withoutTrust(id, "text-" + id, rank, authority, pinned, 0.8, 0);
    }
}
