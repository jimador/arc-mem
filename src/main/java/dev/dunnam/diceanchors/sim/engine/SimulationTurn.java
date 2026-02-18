package dev.dunnam.diceanchors.sim.engine;

import java.util.List;

/**
 * Complete record of a single simulation turn including messages, context, and verdicts.
 */
public record SimulationTurn(
        int turnNumber,
        String playerMessage,
        String dmResponse,
        TurnType turnType,
        AttackStrategy attackStrategy,
        ContextTrace contextTrace,
        List<EvalVerdict> verdicts,
        List<AnchorEvent> anchorEvents
) {
    /**
     * An event that occurred to an anchor during this turn (promotion, reinforcement, eviction).
     *
     * @param turnNumber   1-based turn in which the event occurred
     * @param eventType    lifecycle event: CREATED, REINFORCED, DECAYED, ARCHIVED, EVICTED, AUTHORITY_CHANGED
     * @param anchorId     stable identifier for the affected anchor
     * @param text         anchor proposition text
     * @param authority    authority level after the event
     * @param rank         rank after the event
     * @param previousRank rank before the event (0 for CREATED/EVICTED)
     * @param reason       trigger cause: "sim_extraction", "budget_eviction", "dormancy_decay", "reinforcement", "authority_upgrade"
     */
    public record AnchorEvent(
            int turnNumber,
            String eventType,
            String anchorId,
            String text,
            String authority,
            int rank,
            int previousRank,
            String reason
    ) {}

    /**
     * Returns true if any verdict in this turn is CONTRADICTED.
     */
    public boolean hasContradiction() {
        return verdicts.stream()
                       .anyMatch(v -> v.verdict() == EvalVerdict.Verdict.CONTRADICTED);
    }
}
