package dev.dunnam.diceanchors.anchor;

import java.time.Instant;
import java.util.Map;

/**
 * Captures a trust re-evaluation decision for auditability (ATR1).
 *
 * @param anchorId      the anchor that was re-evaluated
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
        String anchorId,
        String contextId,
        Authority priorAuthority,
        Authority newAuthority,
        double priorTrustScore,
        double newTrustScore,
        String triggerReason,
        Map<String, Double> signalAudit,
        Instant evaluatedAt
) {}
