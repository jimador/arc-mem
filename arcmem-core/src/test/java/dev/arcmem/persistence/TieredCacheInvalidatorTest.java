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
import dev.arcmem.core.memory.event.MemoryUnitLifecycleEvent;
import dev.arcmem.core.memory.event.ArchiveReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TieredCacheInvalidator")
@ExtendWith(MockitoExtension.class)
class TieredCacheInvalidatorTest {

    @Mock
    private MemoryUnitCache cache;
    @Mock
    private MemoryUnitRepository repository;

    private TieredCacheInvalidator invalidator;

    private static final ArcMemProperties.TierConfig TIER_CONFIG =
            new ArcMemProperties.TierConfig(600, 350, 1.5, 1.0, 0.6);

    @BeforeEach
    void setUp() {
        var props = mock(ArcMemProperties.class);
        var unitConfig = mock(ArcMemProperties.UnitConfig.class);
        lenient().when(props.unit()).thenReturn(unitConfig);
        lenient().when(unitConfig.tier()).thenReturn(TIER_CONFIG);
        var storageConfig = new ArcMemProperties.TieredStorageConfig(true, 1000, 60);
        lenient().when(props.tieredStorage()).thenReturn(storageConfig);
        invalidator = new TieredCacheInvalidator(cache, repository, props);
    }

    private PropositionNode hotNode(String id) {
        var node = mock(PropositionNode.class);
        when(node.getId()).thenReturn(id);
        when(node.getText()).thenReturn("text-" + id);
        when(node.getRank()).thenReturn(700); // above hotThreshold=600
        when(node.getAuthority()).thenReturn("RELIABLE");
        when(node.isPinned()).thenReturn(false);
        when(node.getConfidence()).thenReturn(0.9);
        when(node.getReinforcementCount()).thenReturn(1);
        return node;
    }

    private MemoryUnit cachedUnit(String id) {
        return new MemoryUnit(id, "text-" + id, 700, Authority.RELIABLE, false, 0.9, 1,
                null, 0.0, 1.0, MemoryTier.HOT);
    }

    @Nested
    @DisplayName("TierChanged events")
    class TierChangedEvents {

        @Test
        void warmToHotAddsToCache() {
            var node = hotNode("a1");
            when(repository.findActiveUnits("ctx1")).thenReturn(List.of(node));
            var event = MemoryUnitLifecycleEvent.tierChanged(this, "ctx1", "a1", MemoryTier.WARM, MemoryTier.HOT);
            invalidator.onTierChanged(event);
            verify(cache).put(eq("ctx1"), org.mockito.ArgumentMatchers.argThat(a -> a.id().equals("a1")));
        }

        @Test
        void hotToWarmRemovesFromCache() {
            var event = MemoryUnitLifecycleEvent.tierChanged(this, "ctx1", "a1", MemoryTier.HOT, MemoryTier.WARM);
            invalidator.onTierChanged(event);
            verify(cache).invalidate("ctx1", "a1");
        }

        @Test
        void hotToColdRemovesFromCache() {
            var event = MemoryUnitLifecycleEvent.tierChanged(this, "ctx1", "a1", MemoryTier.HOT, MemoryTier.COLD);
            invalidator.onTierChanged(event);
            verify(cache).invalidate("ctx1", "a1");
        }

        @Test
        void warmToColdDoesNotTouchCache() {
            var event = MemoryUnitLifecycleEvent.tierChanged(this, "ctx1", "a1", MemoryTier.WARM, MemoryTier.COLD);
            invalidator.onTierChanged(event);
            verify(cache, never()).invalidate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
            verify(cache, never()).put(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("Evicted and Archived events")
    class EvictedAndArchivedEvents {

        @Test
        void evictionRemovesFromCache() {
            var event = MemoryUnitLifecycleEvent.evicted(this, "ctx1", "a1", 500);
            invalidator.onEvicted(event);
            verify(cache).invalidate("ctx1", "a1");
        }

        @Test
        void archiveRemovesFromCache() {
            var event = MemoryUnitLifecycleEvent.archived(this, "ctx1", "a1", ArchiveReason.DORMANCY_DECAY);
            invalidator.onArchived(event);
            verify(cache).invalidate("ctx1", "a1");
        }
    }

    @Nested
    @DisplayName("Reinforced event")
    class ReinforcedEvent {

        @Test
        void reinforcedUpdatesIfInCache() {
            var node = hotNode("a1");
            when(cache.get("ctx1", "a1")).thenReturn(cachedUnit("a1"));
            when(repository.findActiveUnits("ctx1")).thenReturn(List.of(node));
            var event = MemoryUnitLifecycleEvent.reinforced(this, "ctx1", "a1", 650, 700, 2);
            invalidator.onReinforced(event);
            verify(cache).put(eq("ctx1"), org.mockito.ArgumentMatchers.argThat(a -> a.id().equals("a1")));
        }

        @Test
        void reinforcedNoOpIfNotInCache() {
            when(cache.get("ctx1", "a1")).thenReturn(null);
            var event = MemoryUnitLifecycleEvent.reinforced(this, "ctx1", "a1", 650, 700, 2);
            invalidator.onReinforced(event);
            verify(repository, never()).findActiveUnits(org.mockito.ArgumentMatchers.any());
        }
    }
}
