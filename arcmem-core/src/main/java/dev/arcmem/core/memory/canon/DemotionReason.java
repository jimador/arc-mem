package dev.arcmem.core.memory.canon;
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
 * Reason for an authority demotion, carried in
 * {@link dev.arcmem.core.memory.event.MemoryUnitLifecycleEvent.AuthorityChanged}.
 * <p>
 * Demotion reasons provide a structured audit trail for why an unit's authority was
 * reduced. They are also used as the {@code reason} string in
 * {@code AuthorityChanged} events (via {@link #name()}).
 *
 * @see dev.arcmem.core.memory.event.MemoryUnitLifecycleEvent.AuthorityChanged
 */
public enum DemotionReason {

    /**
     * Contradicting evidence found via conflict resolution.
     * Set when the conflict resolver returns {@code DEMOTE_EXISTING} for an incoming
     * proposition that contradicts this unit.
     */
    CONFLICT_EVIDENCE,

    /**
     * Trust re-evaluation scored the unit below the threshold for its current authority
     * level (see unit-trust spec: "Trust ceiling enforcement on re-evaluation").
     * Applied after conflict resolution or at reinforcement milestone thresholds (3x, 7x).
     */
    TRUST_DEGRADATION,

    /**
     * Rank dropped below the authority-specific threshold via decay.
     * Default thresholds: RELIABLE units demoted when rank &lt; 400;
     * UNRELIABLE units demoted when rank &lt; 200.
     */
    RANK_DECAY,

    /**
     * Explicit user or system action via tool call ({@code demoteUnit}) or direct
     * {@link dev.arcmem.core.memory.engine.ArcMemEngine#demote} API call.
     */
    MANUAL
}
