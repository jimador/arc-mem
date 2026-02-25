package dev.dunnam.diceanchors.extract;

import dev.dunnam.diceanchors.anchor.Anchor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects whether a candidate proposition text is semantically equivalent to
 * an existing anchor.
 *
 * <h2>Thread safety</h2>
 * Implementations must be safe for concurrent use. Stateless implementations
 * are inherently safe; stateful ones must synchronize appropriately.
 *
 * <h2>Error contract: fail-open</h2>
 * On any internal error (LLM timeout, parse failure, etc.), implementations
 * MUST return {@code false} (assume unique). A false negative creates a
 * slightly redundant anchor; a false positive silently drops valid propositions
 * and starves the anchor pool.
 */
public interface DuplicateDetector {

    boolean isDuplicate(String candidateText, List<Anchor> existingAnchors);

    /**
     * Evaluate multiple candidates against the same anchor set.
     * Default loops over {@link #isDuplicate}; implementations may override
     * for batch-optimized LLM calls.
     *
     * @return map from candidate text to duplicate status ({@code true} = duplicate)
     */
    default Map<String, Boolean> batchIsDuplicate(List<String> candidateTexts, List<Anchor> existingAnchors) {
        var result = new LinkedHashMap<String, Boolean>();
        for (var candidate : candidateTexts) {
            result.put(candidate, isDuplicate(candidate, existingAnchors));
        }
        return result;
    }
}
