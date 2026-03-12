package dev.dunnam.diceanchors.anchor;

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
            @JsonProperty("contradictingAnchors") List<AnchorMatch> contradictingAnchors
    ) {}

    public record AnchorMatch(
            @JsonProperty("anchorText") String anchorText,
            @JsonProperty("conflictType") @Nullable ConflictType conflictType,
            @JsonProperty("reasoning") @Nullable String reasoning
    ) {}
}
