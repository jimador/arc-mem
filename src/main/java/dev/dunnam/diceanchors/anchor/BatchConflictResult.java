package dev.dunnam.diceanchors.anchor;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * JSON response model for batch conflict detection.
 * Parsed from LLM response to structured Map.
 */
public record BatchConflictResult(
        @JsonProperty("results") List<Entry> results
) {
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
