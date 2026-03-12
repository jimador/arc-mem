package dev.dunnam.diceanchors.extract;

import dev.dunnam.diceanchors.anchor.Anchor;
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
    public boolean isDuplicate(String candidateText, List<Anchor> existingAnchors) {
        if (fastDetector.isDuplicate(candidateText, existingAnchors)) {
            logger.info("Fast-path duplicate detected: '{}'", candidateText);
            return true;
        }
        return llmDetector.isDuplicate(candidateText, existingAnchors);
    }

    @Override
    public Map<String, Boolean> batchIsDuplicate(List<String> candidateTexts, List<Anchor> existingAnchors) {
        if (candidateTexts.isEmpty() || existingAnchors.isEmpty()) {
            var result = new LinkedHashMap<String, Boolean>();
            candidateTexts.forEach(c -> result.put(c, false));
            return result;
        }
        var result = new LinkedHashMap<String, Boolean>();
        var llmBatch = new ArrayList<String>();
        for (var candidate : candidateTexts) {
            if (fastDetector.isDuplicate(candidate, existingAnchors)) {
                logger.info("Fast-path batch duplicate: '{}'", candidate);
                result.put(candidate, true);
            } else {
                llmBatch.add(candidate);
            }
        }
        if (!llmBatch.isEmpty()) {
            var llmResults = llmDetector.batchIsDuplicate(llmBatch, existingAnchors);
            result.putAll(llmResults);
        }
        return result;
    }
}
