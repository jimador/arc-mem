package dev.dunnam.diceanchors.anchor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy interface for detecting conflicts between an incoming proposition and existing anchors.
 * <p>
 * A conflict exists when an incoming statement contradicts, negates, or is semantically
 * incompatible with an established anchor in the same context. Conflict detection is the
 * first stage in the conflict resolution pipeline; it does not mutate any anchor state.
 *
 * <h2>Thread safety</h2>
 * Implementations MUST be thread-safe. Multiple threads may call {@link #detect} and
 * {@link #batchDetect} concurrently with different inputs.
 *
 * <h2>Error handling</h2>
 * Implementations MUST return an empty list (never null, never throw) if detection fails.
 * Callers rely on an empty list to mean "no conflicts detected".
 */
public interface ConflictDetector {

    /**
     * Represents a detected conflict between an existing anchor and an incoming proposition.
     *
     * @param existing     the existing anchor that conflicts with the incoming text
     * @param incomingText the text of the incoming proposition that triggered the conflict
     * @param confidence   confidence that a real conflict exists (0.0 = uncertain, 1.0 = certain);
     *                     used by {@link ConflictResolver} to choose a resolution
     * @param reason       human-readable description of why the conflict was detected
     *                     (e.g., "negation", "contradiction", "partial overlap")
     */
    record Conflict(Anchor existing, String incomingText, double confidence, String reason) {}

    /**
     * Detect conflicts between an incoming statement and the provided existing anchors.
     * <p>
     * Preconditions: {@code incomingText} and {@code existingAnchors} are non-null.
     * Postconditions: result is non-null; may be empty if no conflicts exist.
     * Error handling: MUST return an empty list on failure — never null, never throw.
     *
     * @param incomingText    the new statement to check for conflicts; never null
     * @param existingAnchors the current active anchors to check against; never null
     * @return list of detected conflicts; empty if none found or on any error
     */
    List<Conflict> detect(String incomingText, List<Anchor> existingAnchors);

    /**
     * Batch evaluate multiple candidates against existing anchors.
     * <p>
     * Default implementation calls {@link #detect} per candidate sequentially.
     * Override for batch LLM efficiency (e.g., single prompt for all candidates).
     * <p>
     * Error handling: MUST return a map with empty lists on failure — never null, never throw.
     *
     * @param candidateTexts  list of incoming statements to check; never null
     * @param existingAnchors existing anchors to check against; never null
     * @return map from candidate text to list of conflicts (empty list = no conflicts for that candidate)
     */
    default Map<String, List<Conflict>> batchDetect(List<String> candidateTexts, List<Anchor> existingAnchors) {
        var result = new LinkedHashMap<String, List<Conflict>>();
        for (var candidate : candidateTexts) {
            result.put(candidate, detect(candidate, existingAnchors));
        }
        return result;
    }
}
