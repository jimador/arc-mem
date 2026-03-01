package dev.dunnam.diceanchors.anchor.attention;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.event.AnchorLifecycleEvent;
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
 * Listens to {@link AnchorLifecycleEvent}s and maintains per-anchor sliding attention windows.
 * Publishes {@link AttentionSignal}s when heat, pressure, or cluster-drift thresholds are crossed.
 * When {@code config.enabled()} is false, all listener and query paths are zero-overhead no-ops.
 */
@Component
public class AttentionTracker {

    private static final Logger logger = LoggerFactory.getLogger(AttentionTracker.class);

    private final ConcurrentHashMap<AttentionKey, AttentionWindow> windows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<AttentionKey, EnumSet<AttentionSignalType>> activeSignals = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher publisher;
    private final DiceAnchorsProperties.AttentionConfig config;

    public AttentionTracker(ApplicationEventPublisher publisher, DiceAnchorsProperties properties) {
        this.publisher = publisher;
        this.config = properties.attention() != null
                ? properties.attention()
                : new DiceAnchorsProperties.AttentionConfig(false, Duration.ofMinutes(5), 0.5, 3, 0.7, 0.2, 3, 20);
    }

    @EventListener
    public void onLifecycleEvent(AnchorLifecycleEvent event) {
        if (!config.enabled()) return;

        var contextId = event.getContextId();
        var eventTime = event.getOccurredAt();

        if (event instanceof AnchorLifecycleEvent.Archived archived) {
            removeWindow(archived.getAnchorId(), contextId);
            return;
        }
        if (event instanceof AnchorLifecycleEvent.Evicted evicted) {
            removeWindow(evicted.getAnchorId(), contextId);
            return;
        }

        var entries = extractEntries(event);
        for (var entry : entries) {
            updateWindow(entry.anchorId(), contextId, eventTime, entry.category());
        }

        evaluateClusterDrift(contextId);
    }

    private record EventEntry(String anchorId, EventCategory category) {}

    private enum EventCategory { CONFLICT, REINFORCEMENT, GENERAL }

    private List<EventEntry> extractEntries(AnchorLifecycleEvent event) {
        return switch (event) {
            case AnchorLifecycleEvent.ConflictDetected e ->
                    e.getConflictingAnchorIds().stream()
                            .map(id -> new EventEntry(id, EventCategory.CONFLICT))
                            .toList();
            case AnchorLifecycleEvent.Reinforced e ->
                    List.of(new EventEntry(e.getAnchorId(), EventCategory.REINFORCEMENT));
            case AnchorLifecycleEvent.Promoted e ->
                    List.of(new EventEntry(e.getAnchorId(), EventCategory.GENERAL));
            case AnchorLifecycleEvent.ConflictResolved e ->
                    List.of(new EventEntry(e.getExistingAnchorId(), EventCategory.GENERAL));
            case AnchorLifecycleEvent.AuthorityChanged e ->
                    List.of(new EventEntry(e.getAnchorId(), EventCategory.GENERAL));
            case AnchorLifecycleEvent.TierChanged e ->
                    List.of(new EventEntry(e.getAnchorId(), EventCategory.GENERAL));
            case AnchorLifecycleEvent.Superseded e ->
                    List.of(new EventEntry(e.getPredecessorId(), EventCategory.GENERAL));
            case AnchorLifecycleEvent.InvariantViolation e ->
                    e.getAnchorId() != null
                            ? List.of(new EventEntry(e.getAnchorId(), EventCategory.GENERAL))
                            : List.of();
            case AnchorLifecycleEvent.Archived _, AnchorLifecycleEvent.Evicted _ -> List.of();
        };
    }

    private void updateWindow(String anchorId, String contextId, Instant eventTime, EventCategory category) {
        var key = new AttentionKey(anchorId, contextId);
        var duration = config.windowDuration();

        var updated = windows.compute(key, (k, existing) -> {
            if (existing == null) {
                return switch (category) {
                    case CONFLICT -> AttentionWindow.initialConflict(anchorId, contextId, eventTime);
                    case REINFORCEMENT -> AttentionWindow.initialReinforcement(anchorId, contextId, eventTime);
                    case GENERAL -> AttentionWindow.initial(anchorId, contextId, eventTime);
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
            publishSignal(key.anchorId(), key.contextId(), AttentionSignalType.HEAT_DROP, window, maxEvents);
        }
        if (window.heatScore(maxEvents) >= config.heatPeakThreshold()) {
            active.remove(AttentionSignalType.HEAT_DROP);
        }
    }

    private void checkThreshold(AttentionKey key, AttentionWindow window, EnumSet<AttentionSignalType> active,
                                 AttentionSignalType type, boolean thresholdMet, int maxEvents) {
        if (thresholdMet && !active.contains(type)) {
            active.add(type);
            publishSignal(key.anchorId(), key.contextId(), type, window, maxEvents);
        } else if (!thresholdMet && active.contains(type)) {
            active.remove(type);
        }
    }

    private void publishSignal(String anchorId, String contextId, AttentionSignalType type,
                                AttentionWindow window, int maxEvents) {
        var snapshot = AttentionSnapshot.of(window, maxEvents);
        var signal = new AttentionSignal(this, anchorId, contextId, type, snapshot);
        logger.info("[ATTENTION] {} for anchor {} in context {}", type, anchorId, contextId);
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
        if (droppingWindows.size() >= config.clusterDriftMinAnchors()) {
            var active = activeSignals.computeIfAbsent(clusterKey, k -> EnumSet.noneOf(AttentionSignalType.class));
            if (!active.contains(AttentionSignalType.CLUSTER_DRIFT)) {
                active.add(AttentionSignalType.CLUSTER_DRIFT);
                var snapshot = AttentionSnapshot.ofCluster(droppingWindows, config.maxExpectedEventsPerWindow());
                var signal = new AttentionSignal(this, null, contextId, AttentionSignalType.CLUSTER_DRIFT, snapshot);
                logger.info("[ATTENTION] CLUSTER_DRIFT in context {} ({} anchors)", contextId, droppingWindows.size());
                publisher.publishEvent(signal);
            }
        } else {
            var active = activeSignals.get(clusterKey);
            if (active != null) {
                active.remove(AttentionSignalType.CLUSTER_DRIFT);
            }
        }
    }

    private void removeWindow(String anchorId, String contextId) {
        var key = new AttentionKey(anchorId, contextId);
        windows.remove(key);
        activeSignals.remove(key);
    }

    public Optional<AttentionWindow> getWindow(String anchorId, String contextId) {
        return Optional.ofNullable(windows.get(new AttentionKey(anchorId, contextId)));
    }

    public List<AttentionWindow> getHottestAnchors(String contextId, int limit) {
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
                        e -> e.getKey().anchorId(),
                        e -> AttentionSnapshot.of(e.getValue(), maxEvents)
                ));
    }

    public void cleanupContext(String contextId) {
        windows.keySet().removeIf(k -> k.contextId().equals(contextId));
        activeSignals.keySet().removeIf(k -> k.contextId().equals(contextId));
    }
}
