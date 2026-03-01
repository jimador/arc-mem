package dev.dunnam.diceanchors.anchor.attention;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record AttentionWindow(
        String anchorId,
        String contextId,
        int conflictCount,
        int reinforcementCount,
        int totalEventCount,
        Instant lastEventAt,
        Instant windowStart,
        List<Instant> eventTimestamps
) {

    public AttentionWindow {
        eventTimestamps = List.copyOf(eventTimestamps);
    }

    public double heatScore(int maxExpectedEvents) {
        if (totalEventCount == 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) totalEventCount / maxExpectedEvents);
    }

    public double pressureScore() {
        return totalEventCount == 0 ? 0.0 : (double) conflictCount / totalEventCount;
    }

    public double burstFactor() {
        if (eventTimestamps.size() < 2) {
            return 1.0;
        }
        var span = Duration.between(windowStart, lastEventAt).toMillis();
        if (span == 0) {
            return 1.0;
        }
        var lastQuarterStart = windowStart.plusMillis(span * 3 / 4);
        var lastQuarterCount = eventTimestamps.stream()
                .filter(t -> !t.isBefore(lastQuarterStart))
                .count();
        return (lastQuarterCount / (double) eventTimestamps.size()) / 0.25;
    }

    public AttentionWindow withEvent(Instant eventTime, Duration windowDuration) {
        var newWindowStart = laterOf(windowStart, eventTime.minus(windowDuration));
        var pruned = eventTimestamps.stream()
                .filter(t -> !t.isBefore(newWindowStart))
                .toList();
        var prunedCount = eventTimestamps.size() - pruned.size();
        var newTimestamps = new ArrayList<>(pruned);
        newTimestamps.add(eventTime);

        var newConflictCount = prunedCount > 0 && !eventTimestamps.isEmpty()
                ? Math.max(0, conflictCount - (int) Math.round((double) prunedCount * conflictCount / eventTimestamps.size()))
                : conflictCount;
        var newReinforcementCount = prunedCount > 0 && !eventTimestamps.isEmpty()
                ? Math.max(0, reinforcementCount - (int) Math.round((double) prunedCount * reinforcementCount / eventTimestamps.size()))
                : reinforcementCount;

        return new AttentionWindow(
                anchorId, contextId,
                newConflictCount, newReinforcementCount,
                newTimestamps.size(),
                eventTime, newWindowStart,
                List.copyOf(newTimestamps)
        );
    }

    public AttentionWindow withConflict(Instant eventTime, Duration windowDuration) {
        var updated = withEvent(eventTime, windowDuration);
        return new AttentionWindow(
                updated.anchorId(), updated.contextId(),
                updated.conflictCount() + 1, updated.reinforcementCount(),
                updated.totalEventCount(),
                updated.lastEventAt(), updated.windowStart(),
                updated.eventTimestamps()
        );
    }

    public AttentionWindow withReinforcement(Instant eventTime, Duration windowDuration) {
        var updated = withEvent(eventTime, windowDuration);
        return new AttentionWindow(
                updated.anchorId(), updated.contextId(),
                updated.conflictCount(), updated.reinforcementCount() + 1,
                updated.totalEventCount(),
                updated.lastEventAt(), updated.windowStart(),
                updated.eventTimestamps()
        );
    }

    public static AttentionWindow initial(String anchorId, String contextId, Instant eventTime) {
        return new AttentionWindow(
                anchorId, contextId, 0, 0, 1, eventTime, eventTime, List.of(eventTime)
        );
    }

    public static AttentionWindow initialConflict(String anchorId, String contextId, Instant eventTime) {
        return new AttentionWindow(
                anchorId, contextId, 1, 0, 1, eventTime, eventTime, List.of(eventTime)
        );
    }

    public static AttentionWindow initialReinforcement(String anchorId, String contextId, Instant eventTime) {
        return new AttentionWindow(
                anchorId, contextId, 0, 1, 1, eventTime, eventTime, List.of(eventTime)
        );
    }

    private static Instant laterOf(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
