package dev.arcmem.core.extraction;
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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * JSON response model for batch duplicate detection.
 * Parsed from LLM response to structured Map.
 */
public record BatchDedupResult(
        @JsonProperty("results") List<Entry> results
) {
    public record Entry(
            @JsonProperty("candidate") String candidate,
            @JsonProperty("isDuplicate") boolean isDuplicate
    ) {}
}
