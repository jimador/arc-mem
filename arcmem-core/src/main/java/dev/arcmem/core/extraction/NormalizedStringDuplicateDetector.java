package dev.arcmem.core.extraction;

import dev.arcmem.core.memory.model.MemoryUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Fast duplicate detector using normalized string comparison.
 * Catches exact and near-exact duplicates (case, whitespace, punctuation)
 * without LLM calls.
 * <p>
 * Normalization: lowercase → collapse whitespace → strip non-alphanumeric.
 */
public class NormalizedStringDuplicateDetector implements DuplicateDetector {

    private static final Logger logger = LoggerFactory.getLogger(NormalizedStringDuplicateDetector.class);

    @Override
    public boolean isDuplicate(String candidateText, List<MemoryUnit> units) {
        var normalizedCandidate = normalize(candidateText);
        for (var unit : units) {
            if (normalizedCandidate.equals(normalize(unit.text()))) {
                logger.debug("Fast dedup match: '{}' matches unit '{}'", candidateText, unit.text());
                return true;
            }
        }
        return false;
    }

    static String normalize(String text) {
        return text.toLowerCase()
                   .replaceAll("[^a-z0-9\\s]", "")
                   .replaceAll("\\s+", " ")
                   .trim();
    }
}
