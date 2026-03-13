package dev.arcmem.core.memory.conflict;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.List;

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
