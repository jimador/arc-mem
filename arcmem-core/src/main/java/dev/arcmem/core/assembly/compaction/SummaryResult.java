package dev.arcmem.core.assembly.compaction;
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
 * Result of a summary generation attempt, including metadata about retries and fallback usage.
 *
 * @param summary      the generated summary text
 * @param retryCount   number of retry attempts made (0 means first attempt succeeded)
 * @param fallbackUsed true if the extractive fallback was used instead of LLM generation
 */
public record SummaryResult(String summary, int retryCount, boolean fallbackUsed) {}
