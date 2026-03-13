package dev.arcmem.simulator.engine;
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

import dev.arcmem.core.spi.llm.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

/**
 * Semantic classification for simulation turns in a scenario lifecycle.
 * <p>
 * Each turn type represents a distinct phase in the adversarial testing process:
 * <pre>
 *   WARM_UP
 *      ↓
 *   ESTABLISH (baseline memory formation)
 *      ↓
 *   {ATTACK, DISPLACEMENT, DRIFT} (adversarial challenges)
 *      ↓
 *   RECALL_PROBE (evaluation of memory integrity)
 * </pre>
 * <p>
 * Turns marked as "requiring evaluation" ({@link #requiresEvaluation()}) are checked
 * against ground truth facts to measure adversarial drift resistance.
 */
public enum TurnType {
    /**
     * Initial warm-up phase. Prepares the system before formal testing begins.
     * Typically no evaluation runs; drift metrics are not collected.
     */
    WARM_UP,

    /**
     * Baseline memory establishment. Presents facts and establishes initial unit state.
     * Evaluation does not run on this phase; it establishes the ground truth baseline.
     */
    ESTABLISH,

    /**
     * Direct adversarial attack. Attempts to corrupt or override units via contradiction,
     * false authority, or manipulation. Drift is evaluated against ground truth.
     */
    ATTACK,

    /**
     * Displacement attack. Tries to shift the semantic meaning or scope of units.
     * Drift is evaluated against ground truth.
     */
    DISPLACEMENT,

    /**
     * Gradual drift attack. Introduces subtle, cumulative changes to unit meaning
     * across multiple turns. Drift is evaluated against ground truth.
     */
    DRIFT,

    /**
     * Recall probe. Tests whether the system can correctly retrieve and confirm
     * established facts under pressure. Drift is evaluated against ground truth.
     */
    RECALL_PROBE;

    /**
     * Parse a turn type from a string, falling back to {@code ESTABLISH} on null or unknown.
     */
    public static TurnType fromString(String value) {
        if (value == null || value.isBlank()) {
            return ESTABLISH;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ESTABLISH;
        }
    }

    /**
     * Returns true if this turn type warrants drift evaluation against ground truth.
     */
    public boolean requiresEvaluation() {
        return this == ATTACK || this == DISPLACEMENT || this == DRIFT || this == RECALL_PROBE;
    }
}
