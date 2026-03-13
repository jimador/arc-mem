package dev.arcmem.core.memory.conflict;
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
 * Direction of an authority transition, carried in
 * {@link dev.arcmem.core.memory.event.MemoryUnitLifecycleEvent.AuthorityChanged}.
 *
 * @see dev.arcmem.core.memory.event.MemoryUnitLifecycleEvent.AuthorityChanged
 */
public enum AuthorityChangeDirection {

    /** Authority was increased (e.g., PROVISIONAL → UNRELIABLE via reinforcement). */
    PROMOTED,

    /** Authority was decreased (e.g., RELIABLE → UNRELIABLE via conflict or decay). */
    DEMOTED
}
