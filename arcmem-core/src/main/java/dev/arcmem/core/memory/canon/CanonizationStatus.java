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
 * Lifecycle status of a {@link CanonizationRequest}.
 * <p>
 * Requests start as {@code PENDING} and transition to one of the terminal states:
 * {@code APPROVED}, {@code REJECTED}, or {@code STALE}.
 *
 * @see CanonizationRequest
 */
public enum CanonizationStatus {

    /** Request is awaiting human review. */
    PENDING,

    /** Approved — the authority transition was executed. */
    APPROVED,

    /** Rejected — the authority transition was cancelled. */
    REJECTED,

    /**
     * Stale — the unit's authority changed since the request was created,
     * so the request is no longer applicable.
     */
    STALE
}
