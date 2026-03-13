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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Compares protected content state before and after a compaction cycle
 * to detect content loss (COMPACTION_LOSS drift).
 */
@Component
public class CompactionDriftEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(CompactionDriftEvaluator.class);

    /** A content item that was protected before compaction but missing after. */
    public record CompactionLoss(
            String lostContentId,
            String lostText
    ) {}

    /**
     * Evaluates compaction for content loss by comparing before/after protected content ID sets.
     * Any ID present in {@code protectedIdsBefore} but absent from {@code protectedIdsAfter}
     * is reported as a loss.
     */
    public List<CompactionLoss> evaluate(Set<String> protectedIdsBefore, Set<String> protectedIdsAfter) {
        var losses = protectedIdsBefore.stream()
                                       .filter(id -> !protectedIdsAfter.contains(id))
                                       .map(id -> new CompactionLoss(id, "Protected content lost during compaction: " + id))
                                       .toList();
        if (!losses.isEmpty()) {
            logger.warn("Compaction loss detected: {} items lost", losses.size());
            for (var loss : losses) {
                logger.warn("  COMPACTION_LOSS: {}", loss.lostContentId());
            }
        } else {
            logger.debug("No compaction loss detected");
        }
        return losses;
    }
}
