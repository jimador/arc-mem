package dev.arcmem.core.extraction;

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
