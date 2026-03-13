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

import java.util.List;

/**
 * Result of a context compaction cycle.
 *
 * @param triggerReason       why compaction was triggered (e.g., "token_threshold", "message_threshold", "forced_turn")
 * @param tokensBefore        estimated token count before compaction
 * @param tokensAfter         estimated token count after compaction
 * @param protectedContentIds IDs of content that was protected during compaction
 * @param summary             the generated summary text
 * @param durationMs          wall-clock time for the compaction cycle in milliseconds
 * @param lossEvents          units that were not adequately represented in the compaction summary
 * @param compactionApplied   whether the compaction was actually applied (false on validation failure/rollback)
 * @param retryCount          number of LLM retry attempts during summary generation
 * @param fallbackUsed        whether the extractive fallback was used instead of LLM generation
 */
public record CompactionResult(
        String triggerReason,
        int tokensBefore,
        int tokensAfter,
        List<String> protectedContentIds,
        String summary,
        long durationMs,
        List<CompactionLossEvent> lossEvents,
        boolean compactionApplied,
        int retryCount,
        boolean fallbackUsed
) {}
