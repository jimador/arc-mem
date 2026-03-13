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
 * Compliance obligation strength for unit facts in prompts.
 * Maps to RFC 2119 keyword levels.
 */
public enum ComplianceStrength {
    /** "MUST be preserved" — absolute requirement (CANON, RELIABLE). */
    STRICT,
    /** "SHOULD be trusted" — strong recommendation (UNRELIABLE). */
    MODERATE,
    /** "MAY be reconsidered" — optional (PROVISIONAL). */
    PERMISSIVE
}
