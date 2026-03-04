package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.event.AnchorLifecycleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Composite memory pressure gauge inspired by the Sleeping LLM project (Guo et al., 2025).
 * <p>
 * Computes a 4-dimensional pressure score from budget utilization, conflict rate, decay rate,
 * and compaction rate. Emits threshold breach events via the lifecycle event system.
 * <p>
 * The composite pressure score maps to their edit_pressure metric with non-linear
 * scaling (exponent 1.5) adapted from their consolidation trigger threshold.
 *
 * @see <a href="https://github.com/vbario/sleeping-llm">Sleeping LLM</a>
 */
@Component
public class MemoryPressureGauge {

    private static final Logger logger = LoggerFactory.getLogger(MemoryPressureGauge.class);

    private final ApplicationEventPublisher eventPublisher;
    private final DiceAnchorsProperties properties;
    private final Map<String, ContextPressureState> contextStates;

    public MemoryPressureGauge(ApplicationEventPublisher eventPublisher, DiceAnchorsProperties properties) {
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        this.contextStates = new ConcurrentHashMap<>();
    }

    public PressureScore computePressure(String contextId, int activeCount, int budgetCap) {
        if (properties.pressure() == null || !properties.pressure().enabled()) {
            return PressureScore.zero();
        }

        var state = contextStates.computeIfAbsent(contextId, k -> new ContextPressureState());
        var config = properties.pressure();

        double budgetPressure = Math.pow((double) activeCount / budgetCap, config.budgetExponent());
        budgetPressure = Math.max(0.0, Math.min(1.0, budgetPressure));

        double conflictPressure = state.getEventRate(EventType.CONFLICT, config.conflictWindowSize());
        double decayPressure = state.getEventRate(EventType.DECAY, config.conflictWindowSize());
        double compactionPressure = state.getEventRate(EventType.COMPACTION, config.conflictWindowSize());

        double weightedBudget = budgetPressure * config.budgetWeight();
        double weightedConflict = conflictPressure * config.conflictWeight();
        double weightedDecay = decayPressure * config.decayWeight();
        double weightedCompaction = compactionPressure * config.compactionWeight();

        double total = weightedBudget + weightedConflict + weightedDecay + weightedCompaction;
        total = Math.max(0.0, Math.min(1.0, total));

        var score = new PressureScore(total, weightedBudget, weightedConflict, weightedDecay,
                weightedCompaction, Instant.now());

        state.recordPressureSnapshot(score);
        checkThresholdTransitions(contextId, score, state, config);

        logger.info("Memory pressure for {}: total={} budget={} conflict={} decay={} compaction={}",
                contextId, String.format("%.3f", total), String.format("%.3f", weightedBudget),
                String.format("%.3f", weightedConflict), String.format("%.3f", weightedDecay),
                String.format("%.3f", weightedCompaction));

        state.advanceWindow();

        return score;
    }

    @EventListener
    public void onConflictDetected(AnchorLifecycleEvent.ConflictDetected event) {
        var state = contextStates.computeIfAbsent(event.getContextId(), k -> new ContextPressureState());
        state.incrementEvent(EventType.CONFLICT);
    }

    @EventListener
    public void onAuthorityChanged(AnchorLifecycleEvent.AuthorityChanged event) {
        if (event.getDirection() == AuthorityChangeDirection.DEMOTED) {
            var state = contextStates.computeIfAbsent(event.getContextId(), k -> new ContextPressureState());
            state.incrementEvent(EventType.DECAY);
        }
    }

    public void recordCompaction(String contextId) {
        var state = contextStates.computeIfAbsent(contextId, k -> new ContextPressureState());
        state.incrementEvent(EventType.COMPACTION);
    }

    public List<PressureScore> getHistory(String contextId) {
        var state = contextStates.get(contextId);
        if (state == null) {
            return List.of();
        }
        return state.getHistory();
    }

    public void clearContext(String contextId) {
        contextStates.remove(contextId);
    }

    private void checkThresholdTransitions(String contextId, PressureScore score,
                                          ContextPressureState state,
                                          DiceAnchorsProperties.PressureConfig config) {
        boolean breachesLight = score.total() >= config.lightSweepThreshold();
        boolean breachesFull = score.total() >= config.fullSweepThreshold();

        boolean wasLight = state.isLightThresholdBreached();
        boolean wasFull = state.isFullThresholdBreached();

        if (breachesLight && !wasLight) {
            state.setLightThresholdBreached(true);
            publishThresholdBreachEvent(contextId, score, "LIGHT_SWEEP");
        } else if (!breachesLight) {
            state.setLightThresholdBreached(false);
        }

        if (breachesFull && !wasFull) {
            state.setFullThresholdBreached(true);
            publishThresholdBreachEvent(contextId, score, "FULL_SWEEP");
        } else if (!breachesFull) {
            state.setFullThresholdBreached(false);
        }
    }

    private void publishThresholdBreachEvent(String contextId, PressureScore score, String thresholdType) {
        var event = AnchorLifecycleEvent.pressureThresholdBreached(this, contextId, score, thresholdType);
        eventPublisher.publishEvent(event);
    }

    private enum EventType {
        CONFLICT, DECAY, COMPACTION
    }

    private static class ContextPressureState {
        private final AtomicInteger conflictCount = new AtomicInteger(0);
        private final AtomicInteger decayCount = new AtomicInteger(0);
        private final AtomicInteger compactionCount = new AtomicInteger(0);
        private final List<PressureScore> history = new ArrayList<>();
        private boolean lightThresholdBreached = false;
        private boolean fullThresholdBreached = false;

        void incrementEvent(EventType type) {
            switch (type) {
                case CONFLICT -> conflictCount.incrementAndGet();
                case DECAY -> decayCount.incrementAndGet();
                case COMPACTION -> compactionCount.incrementAndGet();
            }
        }

        double getEventRate(EventType type, int windowSize) {
            int count = switch (type) {
                case CONFLICT -> conflictCount.get();
                case DECAY -> decayCount.get();
                case COMPACTION -> compactionCount.get();
            };
            if (windowSize <= 0) {
                return 0.0;
            }
            return Math.min(1.0, (double) count / windowSize);
        }

        void advanceWindow() {
            conflictCount.set(0);
            decayCount.set(0);
            compactionCount.set(0);
        }

        void recordPressureSnapshot(PressureScore score) {
            history.add(score);
        }

        List<PressureScore> getHistory() {
            return List.copyOf(history);
        }

        void setLightThresholdBreached(boolean breached) {
            this.lightThresholdBreached = breached;
        }

        boolean isLightThresholdBreached() {
            return lightThresholdBreached;
        }

        void setFullThresholdBreached(boolean breached) {
            this.fullThresholdBreached = breached;
        }

        boolean isFullThresholdBreached() {
            return fullThresholdBreached;
        }
    }
}
