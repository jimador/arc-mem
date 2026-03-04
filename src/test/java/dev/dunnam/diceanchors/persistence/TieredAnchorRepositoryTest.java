package dev.dunnam.diceanchors.persistence;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.MemoryTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TieredAnchorRepository")
@ExtendWith(MockitoExtension.class)
class TieredAnchorRepositoryTest {

    @Mock
    private AnchorEngine engine;
    @Mock
    private AnchorCache cache;

    private TieredAnchorRepository repository;

    private static final DiceAnchorsProperties.TierConfig TIER_CONFIG =
            new DiceAnchorsProperties.TierConfig(600, 350, 1.5, 1.0, 0.6);

    @BeforeEach
    void setUp() {
        var props = org.mockito.Mockito.mock(DiceAnchorsProperties.class);
        var anchorConfig = org.mockito.Mockito.mock(DiceAnchorsProperties.AnchorConfig.class);
        when(props.anchor()).thenReturn(anchorConfig);
        when(anchorConfig.tier()).thenReturn(TIER_CONFIG);
        repository = new TieredAnchorRepository(engine, cache, props);
    }

    private Anchor anchor(String id, int rank, MemoryTier tier) {
        return new Anchor(id, "text-" + id, rank, Authority.RELIABLE, false, 0.9, 1,
                null, 0.0, 1.0, tier);
    }

    @Nested
    @DisplayName("findActiveAnchorsForAssembly")
    class FindActiveAnchorsForAssembly {

        @Test
        void hotServedFromCacheAfterPopulation() {
            var hot = anchor("a1", 700, MemoryTier.HOT);
            when(cache.isPopulated("ctx1")).thenReturn(true);
            when(cache.getAll("ctx1")).thenReturn(List.of(hot));
            when(engine.inject("ctx1")).thenReturn(List.of());

            var result = repository.findActiveAnchorsForAssembly("ctx1");

            assertThat(result).contains(hot);
        }

        @Test
        void warmFromRepositoryNotCache() {
            var warm = anchor("a2", 400, MemoryTier.WARM);
            when(cache.isPopulated("ctx1")).thenReturn(true);
            when(cache.getAll("ctx1")).thenReturn(List.of());
            when(engine.inject("ctx1")).thenReturn(List.of(warm));

            var result = repository.findActiveAnchorsForAssembly("ctx1");

            assertThat(result).contains(warm);
        }

        @Test
        void coldExcludedFromAssembly() {
            // rank 200 → COLD (below warmThreshold=350)
            var cold = anchor("a3", 200, MemoryTier.COLD);
            when(cache.isPopulated("ctx1")).thenReturn(false);
            when(engine.inject("ctx1")).thenReturn(List.of(cold));

            var result = repository.findActiveAnchorsForAssembly("ctx1");

            assertThat(result).doesNotContain(cold);
        }

        @Test
        void lazyPopulationOnFirstAccess() {
            var hot = anchor("a1", 700, MemoryTier.HOT);
            when(cache.isPopulated("ctx1")).thenReturn(false);
            when(engine.inject("ctx1")).thenReturn(List.of(hot));

            repository.findActiveAnchorsForAssembly("ctx1");

            verify(cache).putAll("ctx1", List.of(hot));
        }

        @Test
        void hotAndWarmBothReturnedOnLazyLoad() {
            var hot = anchor("a1", 700, MemoryTier.HOT);
            // rank 400 is above warmThreshold=350, below hotThreshold=600 → WARM
            var warm = anchor("a2", 400, MemoryTier.WARM);
            var cold = anchor("a3", 200, MemoryTier.COLD);
            when(cache.isPopulated("ctx1")).thenReturn(false);
            when(engine.inject("ctx1")).thenReturn(List.of(hot, warm, cold));

            var result = repository.findActiveAnchorsForAssembly("ctx1");

            assertThat(result).containsExactlyInAnyOrder(hot, warm);
            assertThat(result).doesNotContain(cold);
        }
    }

    @Nested
    @DisplayName("findAllTiersForContext")
    class FindAllTiersForContext {

        @Test
        void allTiersReturnedForMaintenance() {
            var hot = anchor("a1", 700, MemoryTier.HOT);
            var warm = anchor("a2", 400, MemoryTier.WARM);
            var cold = anchor("a3", 200, MemoryTier.COLD);
            when(engine.findByContext("ctx1")).thenReturn(List.of(hot, warm, cold));

            var result = repository.findAllTiersForContext("ctx1");

            assertThat(result).containsExactlyInAnyOrder(hot, warm, cold);
        }

        @Test
        void doesNotInteractWithCache() {
            when(engine.findByContext("ctx1")).thenReturn(List.of());
            repository.findAllTiersForContext("ctx1");
            verify(cache, never()).getAll(anyString());
        }
    }
}
