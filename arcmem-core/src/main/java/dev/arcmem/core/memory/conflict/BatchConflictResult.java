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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * JSON response model for batch conflict detection.
 * Parsed from LLM response to structured Map.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BatchConflictResult(
        @JsonProperty("results") List<Entry> results
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
            @JsonProperty("candidate") String candidate,
            @JsonProperty("contradictingUnits") List<UnitMatch> contradictingUnits
    ) {}

    public record UnitMatch(
            @JsonProperty("unitText") String unitText,
            @JsonProperty("conflictType") @Nullable ConflictType conflictType,
            @JsonProperty("reasoning") @Nullable String reasoning
    ) {}
}
