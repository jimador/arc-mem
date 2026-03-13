package dev.arcmem.core.assembly.protection;
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
 * A piece of content that should be protected from compaction.
 *
 * @param id       unique identifier for the content (e.g., unit or proposition ID)
 * @param text     the protected text
 * @param priority higher values indicate greater protection priority
 * @param reason   human-readable explanation for why this content is protected
 */
public record ProtectedContent(
        String id,
        String text,
        int priority,
        String reason
) {}
