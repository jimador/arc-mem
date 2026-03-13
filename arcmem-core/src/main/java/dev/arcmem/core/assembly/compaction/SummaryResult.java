package dev.arcmem.core.assembly.compaction;

/**
 * Result of a summary generation attempt, including metadata about retries and fallback usage.
 *
 * @param summary      the generated summary text
 * @param retryCount   number of retry attempts made (0 means first attempt succeeded)
 * @param fallbackUsed true if the extractive fallback was used instead of LLM generation
 */
public record SummaryResult(String summary, int retryCount, boolean fallbackUsed) {}
