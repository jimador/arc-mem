package dev.arcmem.core.extraction;

import dev.arcmem.core.memory.model.MemoryUnit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects whether a candidate proposition text is semantically equivalent to
 * an existing unit.
 *
 * <h2>Thread safety</h2>
 * Implementations must be safe for concurrent use. Stateless implementations
 * are inherently safe; stateful ones must synchronize appropriately.
 *
 * <h2>Error contract: fail-open</h2>
 * On any internal error (LLM timeout, parse failure, etc.), implementations
 * MUST return {@code false} (assume unique). A false negative creates a
 * slightly redundant unit; a false positive silently drops valid propositions
 * and starves the unit pool.
 */
public interface DuplicateDetector {

    boolean isDuplicate(String candidateText, List<MemoryUnit> existingUnits);

    /**
     * Evaluate multiple candidates against the same unit set.
     * Default loops over {@link #isDuplicate}; implementations may override
     * for batch-optimized LLM calls.
     *
     * @return map from candidate text to duplicate status ({@code true} = duplicate)
     */
    default Map<String, Boolean> batchIsDuplicate(List<String> candidateTexts, List<MemoryUnit> existingUnits) {
        var result = new LinkedHashMap<String, Boolean>();
        for (var candidate : candidateTexts) {
            result.put(candidate, isDuplicate(candidate, existingUnits));
        }
        return result;
    }
}
