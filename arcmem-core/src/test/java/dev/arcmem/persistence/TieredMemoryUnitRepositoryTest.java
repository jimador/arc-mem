package dev.arcmem.core.persistence;
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

import dev.arcmem.core.config.ArcMemProperties;
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

@DisplayName("TieredMemoryUnitRepository")
@ExtendWith(MockitoExtension.class)
class TieredMemoryUnitRepositoryTest {

    @Mock
    private ArcMemEngine engine;
    @Mock
    private MemoryUnitCache cache;

    private TieredMemoryUnitRepository repository;

    private static final ArcMemProperties.TierConfig TIER_CONFIG =
            new ArcMemProperties.TierConfig(600, 350, 1.5, 1.0, 0.6);

    @BeforeEach
    void setUp() {
        var props = org.mockito.Mockito.mock(ArcMemProperties.class);
        var unitConfig = org.mockito.Mockito.mock(ArcMemProperties.UnitConfig.class);
        when(props.unit()).thenReturn(unitConfig);
        when(unitConfig.tier()).thenReturn(TIER_CONFIG);
        repository = new TieredMemoryUnitRepository(engine, cache, props);
    }

    private MemoryUnit unit(String id, int rank, MemoryTier tier) {
        return new MemoryUnit(id, "text-" + id, rank, Authority.RELIABLE, false, 0.9, 1,
                null, 0.0, 1.0, tier, null);
    }

    @Nested
    @DisplayName("findActiveMemoryUnitsForAssembly")
    class FindActiveUnitsForAssembly {

        @Test
        void hotServedFromCacheAfterPopulation() {
            var hot = unit("a1", 700, MemoryTier.HOT);
            when(cache.isPopulated("ctx1")).thenReturn(true);
            when(cache.getAll("ctx1")).thenReturn(List.of(hot));
            when(engine.inject("ctx1")).thenReturn(List.of());

            var result = repository.findActiveUnitsForAssembly("ctx1");

            assertThat(result).contains(hot);
        }

        @Test
        void warmFromRepositoryNotCache() {
            var warm = unit("a2", 400, MemoryTier.WARM);
            when(cache.isPopulated("ctx1")).thenReturn(true);
            when(cache.getAll("ctx1")).thenReturn(List.of());
            when(engine.inject("ctx1")).thenReturn(List.of(warm));

            var result = repository.findActiveUnitsForAssembly("ctx1");

            assertThat(result).contains(warm);
        }

        @Test
        void coldExcludedFromAssembly() {
            // rank 200 → COLD (below warmThreshold=350)
            var cold = unit("a3", 200, MemoryTier.COLD);
            when(cache.isPopulated("ctx1")).thenReturn(false);
            when(engine.inject("ctx1")).thenReturn(List.of(cold));

            var result = repository.findActiveUnitsForAssembly("ctx1");

            assertThat(result).doesNotContain(cold);
        }

        @Test
        void lazyPopulationOnFirstAccess() {
            var hot = unit("a1", 700, MemoryTier.HOT);
            when(cache.isPopulated("ctx1")).thenReturn(false);
            when(engine.inject("ctx1")).thenReturn(List.of(hot));

            repository.findActiveUnitsForAssembly("ctx1");

            verify(cache).putAll("ctx1", List.of(hot));
        }

        @Test
        void hotAndWarmBothReturnedOnLazyLoad() {
            var hot = unit("a1", 700, MemoryTier.HOT);
            // rank 400 is above warmThreshold=350, below hotThreshold=600 → WARM
            var warm = unit("a2", 400, MemoryTier.WARM);
            var cold = unit("a3", 200, MemoryTier.COLD);
            when(cache.isPopulated("ctx1")).thenReturn(false);
            when(engine.inject("ctx1")).thenReturn(List.of(hot, warm, cold));

            var result = repository.findActiveUnitsForAssembly("ctx1");

            assertThat(result).containsExactlyInAnyOrder(hot, warm);
            assertThat(result).doesNotContain(cold);
        }
    }

    @Nested
    @DisplayName("findAllTiersForContext")
    class FindAllTiersForContext {

        @Test
        void allTiersReturnedForMaintenance() {
            var hot = unit("a1", 700, MemoryTier.HOT);
            var warm = unit("a2", 400, MemoryTier.WARM);
            var cold = unit("a3", 200, MemoryTier.COLD);
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
