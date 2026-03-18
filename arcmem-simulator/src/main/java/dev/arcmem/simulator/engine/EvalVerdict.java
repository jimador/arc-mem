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
 * Result of evaluating a single ground truth fact against the DM's response.
 * Includes severity classification for contradiction tracking.
 */
public record EvalVerdict(
        String factId,
        Verdict verdict,
        Severity severity,
        int confidence,
        String evidenceQuote,
        String reasoning,
        String explanation
) {
    public enum Verdict {
        /**
         * The DM's response explicitly or implicitly contradicts the ground truth fact.
         */
        CONTRADICTED,
        /**
         * The DM's response affirms the ground truth fact.
         */
        CONFIRMED,
        /**
         * The ground truth fact was not addressed in the DM's response.
         */
        NOT_MENTIONED
    }

    public enum Severity {
        /**
         * No contradiction — used for CONFIRMED and NOT_MENTIONED verdicts.
         */
        NONE,
        /**
         * Ambiguous or partial contradiction.
         */
        MINOR,
        /**
         * Direct denial or reversal of an established fact.
         */
        MAJOR
    }

    /**
     * Convenience factory for a contradiction verdict.
     */
    public static EvalVerdict contradicted(String factId, Severity severity, String explanation) {
        return new EvalVerdict(factId, Verdict.CONTRADICTED, severity, 5, "", "", explanation);
    }

    public static EvalVerdict confirmed(String factId, String explanation) {
        return new EvalVerdict(factId, Verdict.CONFIRMED, Severity.NONE, 5, "", "", explanation);
    }

    public static EvalVerdict notMentioned(String factId) {
        return new EvalVerdict(factId, Verdict.NOT_MENTIONED, Severity.NONE, 0, "", "", "");
    }
}
