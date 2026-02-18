package dev.dunnam.diceanchors.chat;

import com.fasterxml.jackson.annotation.JsonClassDescription;

/**
 * Structured summary of an anchor for LLM tool responses.
 * Carries the essential fields an LLM needs to reason about an anchor:
 * identity, content, rank, authority level, pin status, and confidence.
 */
@JsonClassDescription("Summary of an established fact (anchor) with its rank, authority level, and confidence")
public record AnchorSummary(
        String id,
        String text,
        int rank,
        String authority,
        boolean pinned,
        double confidence
) {}
