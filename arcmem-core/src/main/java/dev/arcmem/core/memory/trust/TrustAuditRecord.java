package dev.arcmem.core.memory.trust;
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

import java.time.Instant;
import java.util.Map;

/**
 * Captures a trust re-evaluation decision for auditability (ATR1).
 *
 * @param unitId      the unit that was re-evaluated
 * @param contextId     context where the evaluation occurred
 * @param priorAuthority authority before re-evaluation
 * @param newAuthority   authority after re-evaluation (may be the same)
 * @param priorTrustScore prior composite trust score (NaN if not available)
 * @param newTrustScore  new composite trust score from evaluation
 * @param triggerReason  what triggered re-evaluation (e.g., "reinforcement", "conflict", "explicit")
 * @param signalAudit   per-signal contributions from the trust evaluator
 * @param evaluatedAt   when the evaluation occurred
 */
public record TrustAuditRecord(
        String unitId,
        String contextId,
        Authority priorAuthority,
        Authority newAuthority,
        double priorTrustScore,
        double newTrustScore,
        String triggerReason,
        Map<String, Double> signalAudit,
        Instant evaluatedAt
) {}
