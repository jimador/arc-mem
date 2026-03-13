package dev.arcmem.simulator.adversary;
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

/**
 * Immutable record of what happened on an adaptive adversary turn.
 * Stored in {@link AttackHistory} to drive tier escalation decisions.
 *
 * @param turn            1-based turn number
 * @param plan            the attack plan executed this turn
 * @param verdictSeverity "CONTRADICTED" if the attack worked; "NONE" if it had no effect
 */
public record AttackOutcome(
        int turn,
        AttackPlan plan,
        String verdictSeverity
) {
    public AttackOutcome {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        verdictSeverity = verdictSeverity != null ? verdictSeverity : "NONE";
    }

    /**
     * Returns true if the attack had any effect (verdictSeverity != "NONE").
     */
    public boolean succeeded() {
        return !"NONE".equals(verdictSeverity);
    }
}
