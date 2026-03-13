package dev.arcmem.simulator.scenario;
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

import dev.arcmem.simulator.engine.*;

import java.util.List;

/**
 * Complete record of a single simulation turn including messages, context, and verdicts.
 * <p>
 * Created after each turn execution in {@link SimulationTurnExecutor}. Contains the player message,
 * DM response, turn type (WARM_UP/ESTABLISH/ATTACK), applied attack strategies, assembled unit context,
 * drift evaluation verdicts, and any unit lifecycle events (promotion, reinforcement, eviction) triggered during the turn.
 */
public record SimulationTurn(
        int turnNumber,
        String playerMessage,
        String dmResponse,
        TurnType turnType,
        List<AttackStrategy> attackStrategies,
        ContextTrace contextTrace,
        List<EvalVerdict> verdicts,
        List<MemoryUnitEvent> unitEvents
) {
    /**
     * An event that occurred to an unit during this turn (promotion, reinforcement, eviction).
     *
     * @param turnNumber   1-based turn in which the event occurred
     * @param eventType    lifecycle event: CREATED, REINFORCED, DECAYED, ARCHIVED, EVICTED, AUTHORITY_CHANGED
     * @param unitId     stable identifier for the affected unit
     * @param text         unit proposition text
     * @param authority    authority level after the event
     * @param rank         rank after the event
     * @param previousRank rank before the event (0 for CREATED/EVICTED)
     * @param reason       trigger cause: "sim_extraction", "budget_eviction", "dormancy_decay", "reinforcement", "authority_upgrade"
     */
    public record MemoryUnitEvent(
            int turnNumber,
            String eventType,
            String unitId,
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
