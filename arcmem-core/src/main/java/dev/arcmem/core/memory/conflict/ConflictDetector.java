package dev.arcmem.core.memory.conflict;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Strategy interface for detecting conflicts between an incoming proposition and existing units.
 * <p>
 * A conflict exists when an incoming statement contradicts, negates, or is semantically
 * incompatible with an established unit in the same context. Conflict detection is the
 * first stage in the conflict resolution pipeline; it does not mutate any unit state.
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
     * Represents a detected conflict between an existing unit and an incoming proposition.
     *
     * @param existing         the existing unit that conflicts with the incoming text
     * @param incomingText     the text of the incoming proposition that triggered the conflict
     * @param confidence       confidence that a real conflict exists (0.0 = uncertain, 1.0 = certain);
     *                         used by {@link ConflictResolver} to choose a resolution
     * @param reason           human-readable description of why the conflict was detected
     * @param detectionQuality how reliably the detection was performed; {@code DEGRADED}
     *                         means the result is a placeholder, not a confirmed conflict
     */
    record Conflict(@Nullable MemoryUnit existing, String incomingText, double confidence, String reason,
                    DetectionQuality detectionQuality, @Nullable ConflictType conflictType) {

        /** Convenience constructor for full-quality detections (backward-compatible). */
        public Conflict(MemoryUnit existing, String incomingText, double confidence, String reason) {
            this(existing, incomingText, confidence, reason, DetectionQuality.FULL, null);
        }

        /** Convenience constructor preserving existing five-argument callers. */
        public Conflict(MemoryUnit existing, String incomingText, double confidence, String reason,
                        DetectionQuality detectionQuality) {
            this(existing, incomingText, confidence, reason, detectionQuality, null);
        }
    }

    /**
     * Detect conflicts between an incoming statement and the provided existing units.
     * <p>
     * Preconditions: {@code incomingText} and {@code existingUnits} are non-null.
     * Postconditions: result is non-null; may be empty if no conflicts exist.
     * Error handling: MUST return an empty list on failure — never null, never throw.
     *
     * @param incomingText    the new statement to check for conflicts; never null
     * @param existingUnits the current active units to check against; never null
     * @return list of detected conflicts; empty if none found or on any error
     */
    List<Conflict> detect(String incomingText, List<MemoryUnit> existingUnits);

    /**
     * Batch evaluate multiple candidates against existing units.
     * <p>
     * Default implementation calls {@link #detect} per candidate sequentially.
     * Override for batch LLM efficiency (e.g., single prompt for all candidates).
     * <p>
     * Error handling: MUST return a map with empty lists on failure — never null, never throw.
     *
     * @param candidateTexts  list of incoming statements to check; never null
     * @param existingUnits existing units to check against; never null
     * @return map from candidate text to list of conflicts (empty list = no conflicts for that candidate)
     */
    default Map<String, List<Conflict>> batchDetect(List<String> candidateTexts, List<MemoryUnit> existingUnits) {
        var result = new LinkedHashMap<String, List<Conflict>>();
        for (var candidate : candidateTexts) {
            result.put(candidate, detect(candidate, existingUnits));
        }
        return result;
    }
}
