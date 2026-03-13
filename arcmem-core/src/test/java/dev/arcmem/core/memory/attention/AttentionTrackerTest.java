package dev.arcmem.core.memory.attention;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("AttentionTracker")
@ExtendWith(MockitoExtension.class)
class AttentionTrackerTest {

    @Mock
    ApplicationEventPublisher publisher;

    private static final ArcMemProperties.AttentionConfig ENABLED =
            new ArcMemProperties.AttentionConfig(true, Duration.ofMinutes(5), 0.5, 3, 0.7, 0.2, 3, 20);

    private static final ArcMemProperties.AttentionConfig DISABLED =
            new ArcMemProperties.AttentionConfig(false, Duration.ofMinutes(5), 0.5, 3, 0.7, 0.2, 3, 20);

    private AttentionTracker tracker(ArcMemProperties.AttentionConfig config) {
        var props = new ArcMemProperties(null, null, null, null, null, null, null, null, null, null, config, null, null, null, null);
        return new AttentionTracker(publisher, props);
    }

    @Nested
    @DisplayName("event processing")
    class EventProcessing {

        @Test
        @DisplayName("creates new window on first event")
        void createsWindowOnFirstEvent() {
            var tracker = tracker(ENABLED);
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.promoted(this, "ctx-1", "p1", "a1", 500));

            assertThat(tracker.getWindow("a1", "ctx-1")).isPresent();
            assertThat(tracker.getWindow("a1", "ctx-1").get().totalEventCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("ConflictDetected updates windows for all conflicting memory units")
        void conflictDetectedUpdatesAll() {
            var tracker = tracker(ENABLED);
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.conflictDetected(
                    this, "ctx-1", "evil text", 3, List.of("a1", "a2", "a3")));

            assertThat(tracker.getWindow("a1", "ctx-1")).isPresent();
            assertThat(tracker.getWindow("a2", "ctx-1")).isPresent();
            assertThat(tracker.getWindow("a3", "ctx-1")).isPresent();
            assertThat(tracker.getWindow("a1", "ctx-1").get().conflictCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Reinforced event increments reinforcementCount on existing window")
        void reinforcedIncrementsCount() {
            var tracker = tracker(ENABLED);
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.promoted(this, "ctx", "p1", "a1", 500));
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.reinforced(this, "ctx", "a1", 500, 520, 1));

            var window = tracker.getWindow("a1", "ctx");
            assertThat(window).isPresent();
            assertThat(window.get().reinforcementCount()).isEqualTo(1);
            assertThat(window.get().totalEventCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Evicted event removes window")
        void evictedRemovesWindow() {
            var tracker = tracker(ENABLED);
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.promoted(this, "ctx", "p1", "a1", 500));
            assertThat(tracker.getWindow("a1", "ctx")).isPresent();

            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.evicted(this, "ctx", "a1", 500));
            assertThat(tracker.getWindow("a1", "ctx")).isEmpty();
        }
    }

    @Nested
    @DisplayName("threshold signals")
    class ThresholdSignals {

        @Test
        @DisplayName("publishes PRESSURE_SPIKE exactly once when threshold crossed")
        void pressureSpikeHysteresis() {
            var tracker = tracker(ENABLED);
            // 3 conflicts out of 3 total events → pressureScore 1.0, conflictCount 3
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.conflictDetected(this, "ctx", "t1", 1, List.of("a1")));
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.conflictDetected(this, "ctx", "t2", 1, List.of("a1")));
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.conflictDetected(this, "ctx", "t3", 1, List.of("a1")));

            var captor = ArgumentCaptor.forClass(AttentionSignal.class);
            verify(publisher, atLeastOnce()).publishEvent(captor.capture());
            var pressureSignals = captor.getAllValues().stream()
                    .filter(s -> s.getSignalType() == AttentionSignalType.PRESSURE_SPIKE)
                    .toList();
            assertThat(pressureSignals).hasSize(1);

            // Fourth conflict must NOT fire another PRESSURE_SPIKE (hysteresis holds)
            reset(publisher);
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.conflictDetected(this, "ctx", "t4", 1, List.of("a1")));
            var captor2 = ArgumentCaptor.forClass(AttentionSignal.class);
            verify(publisher, atMost(1)).publishEvent(captor2.capture());
            if (!captor2.getAllValues().isEmpty()) {
                assertThat(captor2.getAllValues().stream()
                        .noneMatch(s -> s.getSignalType() == AttentionSignalType.PRESSURE_SPIKE)).isTrue();
            }
        }

        @Test
        @DisplayName("hysteresis resets when metric drops below threshold")
        void hysteresisResetsAfterDrop() {
            var tracker = tracker(ENABLED);
            // Build pressure up to PRESSURE_SPIKE (3 conflicts / 3 events = 1.0 >= 0.5, conflictCount >= 3)
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.conflictDetected(this, "ctx", "t1", 1, List.of("a1")));
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.conflictDetected(this, "ctx", "t2", 1, List.of("a1")));
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.conflictDetected(this, "ctx", "t3", 1, List.of("a1")));

            // Dilute with reinforcements to drop pressure below 0.5 (3 conflicts / 8 events = 0.375)
            for (int i = 0; i < 5; i++) {
                tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.reinforced(this, "ctx", "a1", 500, 510, i));
            }

            reset(publisher);
            // Re-trigger with 3 more conflicts (6 conflicts / 11 events = 0.545 >= 0.5, conflictCount 6 >= 3)
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.conflictDetected(this, "ctx", "t5", 1, List.of("a1")));
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.conflictDetected(this, "ctx", "t6", 1, List.of("a1")));
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.conflictDetected(this, "ctx", "t7", 1, List.of("a1")));

            var captor = ArgumentCaptor.forClass(AttentionSignal.class);
            verify(publisher, atLeastOnce()).publishEvent(captor.capture());
            var pressureSignals = captor.getAllValues().stream()
                    .filter(s -> s.getSignalType() == AttentionSignalType.PRESSURE_SPIKE)
                    .toList();
            assertThat(pressureSignals).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("window removal")
    class WindowRemoval {

        @Test
        @DisplayName("Archived event removes window")
        void archivedRemovesWindow() {
            var tracker = tracker(ENABLED);
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.promoted(this, "ctx", "p1", "a1", 500));
            assertThat(tracker.getWindow("a1", "ctx")).isPresent();

            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.archived(this, "ctx", "a1", ArchiveReason.DORMANCY_DECAY));
            assertThat(tracker.getWindow("a1", "ctx")).isEmpty();
        }
    }

    @Nested
    @DisplayName("disabled config")
    class DisabledConfig {

        @Test
        @DisplayName("disabled tracker ignores all events")
        void disabledIgnoresEvents() {
            var tracker = tracker(DISABLED);
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.promoted(this, "ctx", "p1", "a1", 500));

            assertThat(tracker.getWindow("a1", "ctx")).isEmpty();
            verifyNoInteractions(publisher);
        }

        @Test
        @DisplayName("disabled tracker returns empty collections from all query methods")
        void disabledReturnsEmptyCollections() {
            var tracker = tracker(DISABLED);
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.promoted(this, "ctx", "p1", "a1", 500));

            assertThat(tracker.getAllWindows("ctx")).isEmpty();
            assertThat(tracker.getHottestUnits("ctx", 5)).isEmpty();
            assertThat(tracker.snapshot("ctx")).isEmpty();
        }
    }

    @Nested
    @DisplayName("query API")
    class QueryApi {

        @Test
        @DisplayName("getHottestMemoryUnits returns correct order and limit")
        void hottestUnitsOrderAndLimit() {
            var tracker = tracker(ENABLED);
            // a1 gets 1 event
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.promoted(this, "ctx", "p1", "a1", 500));
            // a2 gets 3 events (hottest)
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.promoted(this, "ctx", "p2", "a2", 500));
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.reinforced(this, "ctx", "a2", 500, 510, 1));
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.reinforced(this, "ctx", "a2", 510, 520, 2));
            // a3 gets 2 events
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.promoted(this, "ctx", "p3", "a3", 500));
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.reinforced(this, "ctx", "a3", 500, 510, 1));

            var hottest = tracker.getHottestUnits("ctx", 2);

            assertThat(hottest).hasSize(2);
            assertThat(hottest.getFirst().unitId()).isEqualTo("a2");
        }

        @Test
        @DisplayName("cleanupContext removes all state")
        void cleanupRemovesAll() {
            var tracker = tracker(ENABLED);
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.promoted(this, "ctx", "p1", "a1", 500));
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.promoted(this, "ctx", "p2", "a2", 500));

            tracker.cleanupContext("ctx");

            assertThat(tracker.getAllWindows("ctx")).isEmpty();
            assertThat(tracker.snapshot("ctx")).isEmpty();
        }

        @Test
        @DisplayName("query empty context returns empty collections")
        void emptyContextReturnsEmpty() {
            var tracker = tracker(ENABLED);

            assertThat(tracker.getWindow("a1", "unknown")).isEmpty();
            assertThat(tracker.getAllWindows("unknown")).isEmpty();
            assertThat(tracker.getHottestUnits("unknown", 5)).isEmpty();
            assertThat(tracker.snapshot("unknown")).isEmpty();
        }

        @Test
        @DisplayName("getAllWindows includes only windows for the given context")
        void getAllWindowsIsolatesContext() {
            var tracker = tracker(ENABLED);
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.promoted(this, "ctx-A", "p1", "a1", 500));
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.promoted(this, "ctx-B", "p2", "a2", 500));

            assertThat(tracker.getAllWindows("ctx-A")).hasSize(1);
            assertThat(tracker.getAllWindows("ctx-A").getFirst().unitId()).isEqualTo("a1");
            assertThat(tracker.getAllWindows("ctx-B")).hasSize(1);
        }

        @Test
        @DisplayName("snapshot keys match contextUnitIds of all tracked windows")
        void snapshotKeysMatchUnitIds() {
            var tracker = tracker(ENABLED);
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.promoted(this, "ctx", "p1", "a1", 500));
            tracker.onLifecycleEvent(MemoryUnitLifecycleEvent.promoted(this, "ctx", "p2", "a2", 500));

            var snap = tracker.snapshot("ctx");

            assertThat(snap.keySet()).containsExactlyInAnyOrder("a1", "a2");
        }
    }
}
