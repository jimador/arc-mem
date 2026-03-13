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

import java.util.List;

/**
 * SPI for components that declare content as protected from compaction.
 * Implementations identify content that must survive summarization.
 */
public interface ProtectedContentProvider {

    /**
     * Returns all content that should be protected from compaction in the given context,
     * ordered by priority descending.
     */
    List<ProtectedContent> getProtectedContent(String contextId);
}
