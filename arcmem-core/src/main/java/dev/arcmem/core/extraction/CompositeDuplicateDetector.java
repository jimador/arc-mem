package dev.arcmem.core.extraction;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chains fast normalized-string matching with LLM-based detection.
 * Fast-path matches short-circuit without an LLM call; misses delegate to
 * the LLM detector.
 */
public class CompositeDuplicateDetector implements DuplicateDetector {

    private static final Logger logger = LoggerFactory.getLogger(CompositeDuplicateDetector.class);

    private final NormalizedStringDuplicateDetector fastDetector;
    private final LlmDuplicateDetector llmDetector;

    public CompositeDuplicateDetector(NormalizedStringDuplicateDetector fastDetector,
                                      LlmDuplicateDetector llmDetector) {
        this.fastDetector = fastDetector;
        this.llmDetector = llmDetector;
    }

    @Override
    public boolean isDuplicate(String candidateText, List<MemoryUnit> existingUnits) {
        if (fastDetector.isDuplicate(candidateText, existingUnits)) {
            logger.info("Fast-path duplicate detected: '{}'", candidateText);
            return true;
        }
        return llmDetector.isDuplicate(candidateText, existingUnits);
    }

    @Override
    public Map<String, Boolean> batchIsDuplicate(List<String> candidateTexts, List<MemoryUnit> existingUnits) {
        if (candidateTexts.isEmpty() || existingUnits.isEmpty()) {
            var result = new LinkedHashMap<String, Boolean>();
            candidateTexts.forEach(c -> result.put(c, false));
            return result;
        }
        var result = new LinkedHashMap<String, Boolean>();
        var llmBatch = new ArrayList<String>();
        for (var candidate : candidateTexts) {
            if (fastDetector.isDuplicate(candidate, existingUnits)) {
                logger.info("Fast-path batch duplicate: '{}'", candidate);
                result.put(candidate, true);
            } else {
                llmBatch.add(candidate);
            }
        }
        if (!llmBatch.isEmpty()) {
            var llmResults = llmDetector.batchIsDuplicate(llmBatch, existingUnits);
            result.putAll(llmResults);
        }
        return result;
    }
}
