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
 * Immutable trust evaluation result for a proposition.
 * <p>
 * Invariant T1: score is always in [0.0, 1.0].
 * Invariant T2: authorityCeiling is never CANON (per invariant A3).
 * Invariant T3: signalAudit contains weighted contributions for all evaluated signals.
 *
 * @param score            composite trust score in [0.0, 1.0]
 * @param authorityCeiling maximum authority this score can warrant; never CANON
 * @param promotionZone    routing zone based on domain profile thresholds
 * @param signalAudit      mapping of signal names to their weighted contributions
 * @param evaluatedAt      timestamp when this score was computed
 */
public record TrustScore(
        double score,
        Authority authorityCeiling,
        PromotionZone promotionZone,
        Map<String, Double> signalAudit,
        Instant evaluatedAt
) {}
