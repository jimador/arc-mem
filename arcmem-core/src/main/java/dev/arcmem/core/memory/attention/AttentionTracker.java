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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Listens to {@link MemoryUnitLifecycleEvent}s and maintains per-unit sliding attention windows.
 * Publishes {@link AttentionSignal}s when heat, pressure, or cluster-drift thresholds are crossed.
 * When {@code config.enabled()} is false, all listener and query paths are zero-overhead no-ops.
 */
@Component
public class AttentionTracker {

    private static final Logger logger = LoggerFactory.getLogger(AttentionTracker.class);

    private final ConcurrentHashMap<AttentionKey, AttentionWindow> windows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<AttentionKey, EnumSet<AttentionSignalType>> activeSignals = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher publisher;
    private final ArcMemProperties.AttentionConfig config;

    public AttentionTracker(ApplicationEventPublisher publisher, ArcMemProperties properties) {
        this.publisher = publisher;
        this.config = properties.attention() != null
                ? properties.attention()
                : new ArcMemProperties.AttentionConfig(false, Duration.ofMinutes(5), 0.5, 3, 0.7, 0.2, 3, 20);
    }

    @EventListener
    public void onLifecycleEvent(MemoryUnitLifecycleEvent event) {
        if (!config.enabled()) return;

        var contextId = event.getContextId();
        var eventTime = event.getOccurredAt();

        if (event instanceof MemoryUnitLifecycleEvent.Archived archived) {
            removeWindow(archived.getUnitId(), contextId);
            return;
        }
        if (event instanceof MemoryUnitLifecycleEvent.Evicted evicted) {
            removeWindow(evicted.getUnitId(), contextId);
            return;
        }

        var entries = extractEntries(event);
        for (var entry : entries) {
            updateWindow(entry.unitId(), contextId, eventTime, entry.category());
        }

        evaluateClusterDrift(contextId);
    }

    private record EventEntry(String unitId, EventCategory category) {}

    private enum EventCategory { CONFLICT, REINFORCEMENT, GENERAL }

    private List<EventEntry> extractEntries(MemoryUnitLifecycleEvent event) {
        return switch (event) {
            case MemoryUnitLifecycleEvent.ConflictDetected e ->
                    e.getConflictingUnitIds().stream()
                            .map(id -> new EventEntry(id, EventCategory.CONFLICT))
                            .toList();
            case MemoryUnitLifecycleEvent.Reinforced e ->
                    List.of(new EventEntry(e.getUnitId(), EventCategory.REINFORCEMENT));
            case MemoryUnitLifecycleEvent.Promoted e ->
                    List.of(new EventEntry(e.getUnitId(), EventCategory.GENERAL));
            case MemoryUnitLifecycleEvent.ConflictResolved e ->
                    List.of(new EventEntry(e.getExistingUnitId(), EventCategory.GENERAL));
            case MemoryUnitLifecycleEvent.AuthorityChanged e ->
                    List.of(new EventEntry(e.getUnitId(), EventCategory.GENERAL));
            case MemoryUnitLifecycleEvent.TierChanged e ->
                    List.of(new EventEntry(e.getUnitId(), EventCategory.GENERAL));
            case MemoryUnitLifecycleEvent.Superseded e ->
                    List.of(new EventEntry(e.getPredecessorId(), EventCategory.GENERAL));
            case MemoryUnitLifecycleEvent.InvariantViolation e ->
                    e.getUnitId() != null
                            ? List.of(new EventEntry(e.getUnitId(), EventCategory.GENERAL))
                            : List.of();
            case MemoryUnitLifecycleEvent.Archived _, MemoryUnitLifecycleEvent.Evicted _,
                 MemoryUnitLifecycleEvent.PressureThresholdBreached _ -> List.of();
        };
    }

    private void updateWindow(String unitId, String contextId, Instant eventTime, EventCategory category) {
        var key = new AttentionKey(unitId, contextId);
        var duration = config.windowDuration();

        var updated = windows.compute(key, (k, existing) -> {
            if (existing == null) {
                return switch (category) {
                    case CONFLICT -> AttentionWindow.initialConflict(unitId, contextId, eventTime);
                    case REINFORCEMENT -> AttentionWindow.initialReinforcement(unitId, contextId, eventTime);
                    case GENERAL -> AttentionWindow.initial(unitId, contextId, eventTime);
                };
            }
            return switch (category) {
                case CONFLICT -> existing.withConflict(eventTime, duration);
                case REINFORCEMENT -> existing.withReinforcement(eventTime, duration);
                case GENERAL -> existing.withEvent(eventTime, duration);
            };
        });

        evaluateThresholds(key, updated);
    }

    private void evaluateThresholds(AttentionKey key, AttentionWindow window) {
        var active = activeSignals.computeIfAbsent(key, k -> EnumSet.noneOf(AttentionSignalType.class));
        var maxEvents = config.maxExpectedEventsPerWindow();

        checkThreshold(key, window, active, AttentionSignalType.PRESSURE_SPIKE,
                window.pressureScore() >= config.pressureThreshold()
                        && window.conflictCount() >= config.minConflictsForPressure(),
                maxEvents);

        checkThreshold(key, window, active, AttentionSignalType.HEAT_PEAK,
                window.heatScore(maxEvents) >= config.heatPeakThreshold(),
                maxEvents);

        var heatDropCondition = active.contains(AttentionSignalType.HEAT_PEAK)
                && window.heatScore(maxEvents) < config.heatDropThreshold();
        if (heatDropCondition && !active.contains(AttentionSignalType.HEAT_DROP)) {
            active.add(AttentionSignalType.HEAT_DROP);
            publishSignal(key.unitId(), key.contextId(), AttentionSignalType.HEAT_DROP, window, maxEvents);
        }
        if (window.heatScore(maxEvents) >= config.heatPeakThreshold()) {
            active.remove(AttentionSignalType.HEAT_DROP);
        }
    }

    private void checkThreshold(AttentionKey key, AttentionWindow window, EnumSet<AttentionSignalType> active,
                                 AttentionSignalType type, boolean thresholdMet, int maxEvents) {
        if (thresholdMet && !active.contains(type)) {
            active.add(type);
            publishSignal(key.unitId(), key.contextId(), type, window, maxEvents);
        } else if (!thresholdMet && active.contains(type)) {
            active.remove(type);
        }
    }

    private void publishSignal(String unitId, String contextId, AttentionSignalType type,
                                AttentionWindow window, int maxEvents) {
        var snapshot = AttentionSnapshot.of(window, maxEvents);
        var signal = new AttentionSignal(this, unitId, contextId, type, snapshot);
        logger.info("[ATTENTION] {} for unit {} in context {}", type, unitId, contextId);
        publisher.publishEvent(signal);
    }

    private void evaluateClusterDrift(String contextId) {
        var droppingWindows = windows.entrySet().stream()
                .filter(e -> e.getKey().contextId().equals(contextId))
                .filter(e -> {
                    var signals = activeSignals.get(e.getKey());
                    return signals != null && signals.contains(AttentionSignalType.HEAT_DROP);
                })
                .map(Map.Entry::getValue)
                .toList();

        var clusterKey = new AttentionKey("__cluster__", contextId);
        if (droppingWindows.size() >= config.clusterDriftMinUnits()) {
            var active = activeSignals.computeIfAbsent(clusterKey, k -> EnumSet.noneOf(AttentionSignalType.class));
            if (!active.contains(AttentionSignalType.CLUSTER_DRIFT)) {
                active.add(AttentionSignalType.CLUSTER_DRIFT);
                var snapshot = AttentionSnapshot.ofCluster(droppingWindows, config.maxExpectedEventsPerWindow());
                var signal = new AttentionSignal(this, null, contextId, AttentionSignalType.CLUSTER_DRIFT, snapshot);
                logger.info("[ATTENTION] CLUSTER_DRIFT in context {} ({} units)", contextId, droppingWindows.size());
                publisher.publishEvent(signal);
            }
        } else {
            var active = activeSignals.get(clusterKey);
            if (active != null) {
                active.remove(AttentionSignalType.CLUSTER_DRIFT);
            }
        }
    }

    private void removeWindow(String unitId, String contextId) {
        var key = new AttentionKey(unitId, contextId);
        windows.remove(key);
        activeSignals.remove(key);
    }

    public Optional<AttentionWindow> getWindow(String unitId, String contextId) {
        return Optional.ofNullable(windows.get(new AttentionKey(unitId, contextId)));
    }

    public List<AttentionWindow> getHottestUnits(String contextId, int limit) {
        var maxEvents = config.maxExpectedEventsPerWindow();
        return windows.entrySet().stream()
                .filter(e -> e.getKey().contextId().equals(contextId))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparingDouble((AttentionWindow w) -> w.heatScore(maxEvents)).reversed())
                .limit(limit)
                .toList();
    }

    public List<AttentionWindow> getAllWindows(String contextId) {
        return windows.entrySet().stream()
                .filter(e -> e.getKey().contextId().equals(contextId))
                .map(Map.Entry::getValue)
                .toList();
    }

    public Map<String, AttentionSnapshot> snapshot(String contextId) {
        var maxEvents = config.maxExpectedEventsPerWindow();
        return windows.entrySet().stream()
                .filter(e -> e.getKey().contextId().equals(contextId))
                .collect(Collectors.toMap(
                        e -> e.getKey().unitId(),
                        e -> AttentionSnapshot.of(e.getValue(), maxEvents)
                ));
    }

    public void cleanupContext(String contextId) {
        windows.keySet().removeIf(k -> k.contextId().equals(contextId));
        activeSignals.keySet().removeIf(k -> k.contextId().equals(contextId));
    }
}
