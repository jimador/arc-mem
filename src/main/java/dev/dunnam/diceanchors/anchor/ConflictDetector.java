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
 *
 * <h2>Detection quality (ACON1)</h2>
 * When parse or LLM failures occur, implementations MUST set
 * {@link DetectionQuality#DEGRADED} on returned {@link Conflict} records rather than
 * silently returning an empty list. Callers and reports use this to surface degraded
 * conflict detection counts.
 */
public interface ConflictDetector {

    /**
     * Quality of the detection that produced a {@link Conflict}.
     * <ul>
     *   <li>{@code FULL} — normal detection path with structured LLM or lexical response</li>
     *   <li>{@code FALLBACK} — LLM parse failed but keyword heuristic found a signal</li>
     *   <li>{@code DEGRADED} — detection could not complete; result is a placeholder
     *       marking the candidate as needing manual review</li>
     * </ul>
     */
    enum DetectionQuality { FULL, FALLBACK, DEGRADED }

    /**
     * Represents a detected conflict between an existing anchor and an incoming proposition.
     *
     * @param existing         the existing anchor that conflicts with the incoming text
     * @param incomingText     the text of the incoming proposition that triggered the conflict
     * @param confidence       confidence that a real conflict exists (0.0 = uncertain, 1.0 = certain);
     *                         used by {@link ConflictResolver} to choose a resolution
     * @param reason           human-readable description of why the conflict was detected
     * @param detectionQuality how reliably the detection was performed; {@code DEGRADED}
     *                         means the result is a placeholder, not a confirmed conflict
     */
    record Conflict(Anchor existing, String incomingText, double confidence, String reason,
                    DetectionQuality detectionQuality) {

        /** Convenience constructor for full-quality detections (backward-compatible). */
        public Conflict(Anchor existing, String incomingText, double confidence, String reason) {
            this(existing, incomingText, confidence, reason, DetectionQuality.FULL);
        }
    }

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
