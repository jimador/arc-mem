package dev.arcmem.core.extraction;

import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.core.memory.canon.DemotionReason;
import dev.arcmem.core.memory.conflict.ConflictDetector;
import dev.arcmem.core.memory.conflict.ConflictIndex;
import dev.arcmem.core.memory.conflict.ConflictResolver;
import dev.arcmem.core.memory.conflict.ConflictType;
import dev.arcmem.core.memory.conflict.ResolutionContext;
import dev.arcmem.core.memory.conflict.SourceAuthorityResolver;
import dev.arcmem.core.memory.engine.ArcMemEngine;
import dev.arcmem.core.memory.event.ArchiveReason;
import dev.arcmem.core.memory.model.Authority;
import dev.arcmem.core.memory.model.MemoryUnit;
import dev.arcmem.core.memory.model.PromotionZone;
import dev.arcmem.core.memory.model.SemanticUnit;
import dev.arcmem.core.memory.trust.TrustContext;
import dev.arcmem.core.memory.trust.TrustPipeline;
import dev.arcmem.core.memory.trust.TrustScore;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Evaluates {@link SemanticUnit}s for promotion to memory unit status through a
 * multi-gate pipeline.
 *
 * <h2>Pipeline Gates</h2>
 * <ol>
 *   <li><strong>Confidence gate</strong> — drops units below the configured
 *       {@code autoActivateThreshold}. Fast, no I/O.</li>
 *   <li><strong>Conflict pre-check gate</strong> — rejects units that conflict
 *       with RELIABLE+ units according to the precomputed {@link ConflictIndex}.
 *       O(1) lookup per unit pair, no LLM calls. Skipped when the index is absent
 *       or empty. Inspired by the Sleeping LLM hallucination firewall pattern
 *       (Guo et al., 2025) — filter contradictions before injection rather than
 *       detecting them after the fact. Uses {@link ConflictIndex} (F05) for O(1) lookup.</li>
 *   <li><strong>Dedup gate</strong> — rejects units that are semantically
 *       identical to existing active memory units. Uses {@link DuplicateDetector} which
 *       combines fast normalized-string matching with optional LLM verification.
 *       In batch mode, an exact-text cross-reference against existing units runs
 *       first (no LLM call), followed by intra-batch dedup, before the LLM batch
 *       dedup call.</li>
 *   <li><strong>Conflict gate</strong> — detects semantic contradictions with existing
 *       memory units and applies {@link ConflictResolver} to decide the outcome.
 *       KEEP_EXISTING rejects the incoming unit. REPLACE archives the existing
 *       memory unit. DEMOTE_EXISTING demotes the existing memory unit one authority level.
 *       COEXIST allows both. After resolution, trust is re-evaluated for any surviving
 *       existing unit (KEEP_EXISTING, DEMOTE_EXISTING, COEXIST outcomes).</li>
 *   <li><strong>Trust gate</strong> — runs the {@link TrustPipeline} on each candidate.
 *       Units in the REVIEW or ARCHIVE zone are filtered out. The trust score's
 *       {@link TrustScore#authorityCeiling()} is passed to
 *       {@link ArcMemEngine#promote(String, int, Authority)} so the initial authority
 *       ceiling is set at promotion time.</li>
 *   <li><strong>Promote</strong> — calls {@link ArcMemEngine#promote} for each surviving
 *       unit. Budget enforcement (eviction of lowest-ranked non-pinned units)
 *       is applied per-promote call.</li>
 * </ol>
 *
 * <h2>Batch vs. Sequential Processing</h2>
 * {@link #batchEvaluateAndPromote} processes all candidates together through Gates 1–5
 * using batch LLM calls, then promotes sequentially to preserve budget enforcement.
 * {@link #evaluateAndPromote} processes each unit independently through the full
 * gate sequence. Both methods produce the same result for a single unit; batch
 * processing is more efficient for many candidates.
 *
 * <h2>Relationship to ArcMemEngine</h2>
 * {@code SemanticUnitPromoter} orchestrates the promotion pipeline — it decides whether and
 * how to promote. {@link ArcMemEngine} executes unit lifecycle operations
 * (promote, archive, demote, reinforce). The promoter calls the engine; the engine
 * never calls back into the promoter.
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li><strong>P1</strong>: Duplicate units MUST NOT produce separate memory units.
 *       The dedup gate (both LLM and exact-text cross-reference) enforces this.</li>
 *   <li><strong>P2</strong>: Conflict resolution decisions MUST always be acted upon.
 *       Every non-null resolution outcome changes unit state.</li>
 *   <li><strong>P3</strong>: Gate sequence is preserved — confidence → conflict pre-check
 *       → dedup → conflict → trust → promote. The pre-check gate is skipped when
 *       {@link ConflictIndex} is absent or empty.</li>
 *   <li><strong>P4</strong>: Budget enforcement is applied per {@link ArcMemEngine#promote}
 *       call, not at the batch level. Batch size is capped at the configured maximum.</li>
 * </ul>
 */
@Service
public class SemanticUnitPromoter {

    private static final Logger logger = LoggerFactory.getLogger(SemanticUnitPromoter.class);

    public record PromotionOutcome(int promotedCount, int degradedConflictCount) {
        public static PromotionOutcome empty() {
            return new PromotionOutcome(0, 0);
        }
    }

    private record ConflictResolutionResult(boolean accepted, int degradedConflictCount) {
        static ConflictResolutionResult rejected(int degradedConflictCount) {
            return new ConflictResolutionResult(false, degradedConflictCount);
        }
    }

    private final ArcMemEngine engine;
    private final ArcMemProperties properties;
    private final TrustPipeline trustPipeline;
    private final MemoryUnitRepository repository;
    private final DuplicateDetector duplicateDetector;
    @Nullable
    private final ConflictIndex conflictIndex;
    @Nullable
    private final SourceAuthorityResolver sourceAuthorityResolver;

    public SemanticUnitPromoter(ArcMemEngine engine, ArcMemProperties properties,
                                TrustPipeline trustPipeline, MemoryUnitRepository repository,
                                DuplicateDetector duplicateDetector,
                                Optional<ConflictIndex> conflictIndex,
                                Optional<SourceAuthorityResolver> sourceAuthorityResolver) {
        this.engine = engine;
        this.properties = properties;
        this.trustPipeline = trustPipeline;
        this.repository = repository;
        this.duplicateDetector = duplicateDetector;
        this.conflictIndex = conflictIndex.orElse(null);
        this.sourceAuthorityResolver = sourceAuthorityResolver.orElse(null);
    }

    public int evaluateAndPromote(String contextId, List<? extends SemanticUnit> units) {
        return evaluateAndPromoteWithOutcome(contextId, units).promotedCount();
    }

    public PromotionOutcome evaluateAndPromoteWithOutcome(String contextId, List<? extends SemanticUnit> units) {
        int total = 0;
        int postConfidence = 0;
        int postPrecheck = 0;
        int postDedup = 0;
        int postConflict = 0;
        int postTrust = 0;
        int promoted = 0;
        int degradedConflictCount = 0;

        var threshold = properties.unit().autoActivateThreshold();
        var initialRank = properties.unit().initialRank();
        var existingUnits = engine.inject(contextId);

        for (var unit : units) {
            if (!unit.isPromotionCandidate()) {
                continue;
            }
            total++;

            if (unit.confidence() < threshold) {
                logger.debug("Skipping unit {} - confidence {} below threshold {}",
                             unit.id(), unit.confidence(), threshold);
                continue;
            }
            postConfidence++;

            if (shouldRunPrecheck() && isFilteredByPrecheck(unit.text(), existingUnits)) {
                continue;
            }
            postPrecheck++;

            if (duplicateDetector.isDuplicate(unit.text(), existingUnits)) {
                logger.info("Unit {} is duplicate, filtering at dedup gate", unit.id());
                continue;
            }
            postDedup++;

            var conflicts = engine.detectConflicts(contextId, unit.text());
            if (!conflicts.isEmpty()) {
                var resolutionResult = resolveConflicts(unit, conflicts);
                degradedConflictCount += resolutionResult.degradedConflictCount();
                if (!resolutionResult.accepted()) {
                    continue;
                }
            }
            postConflict++;

            TrustScore trustScore = null;
            var nodeOpt = repository.findPropositionNodeById(unit.id());
            if (nodeOpt.isPresent()) {
                var node = nodeOpt.get();
                logger.debug("Trust gate: unit {} sourceIds={} confidence={}",
                             unit.id(), node.getSourceIds(), node.getConfidence());
                trustScore = trustPipeline.evaluate(node, contextId);
                logger.info("Trust gate: unit {} score={} zone={} audit={}",
                            unit.id(), trustScore.score(), trustScore.promotionZone(),
                            trustScore.signalAudit());
                if (trustScore.promotionZone() == PromotionZone.REVIEW) {
                    logger.info("Unit {} in REVIEW zone (score={}), skipping auto-promotion",
                                unit.id(), trustScore.score());
                    continue;
                }
                if (trustScore.promotionZone() == PromotionZone.ARCHIVE) {
                    logger.info("Unit {} in ARCHIVE zone (score={}), skipping",
                                unit.id(), trustScore.score());
                    continue;
                }
            } else {
                logger.warn("Trust gate: unit {} not found in repository, skipping trust evaluation",
                            unit.id());
            }
            postTrust++;

            if (trustScore != null) {
                engine.promote(unit.id(), initialRank, trustScore.authorityCeiling());
            } else {
                engine.promote(unit.id(), initialRank);
            }
            promoted++;
        }

        logger.info("Promotion funnel for context {}: {} active, {} post-confidence, {} post-precheck, " +
                    "{} post-dedup, {} post-conflict, {} post-trust, {} promoted, {} degraded-conflict(s)",
                    contextId, total, postConfidence, postPrecheck, postDedup, postConflict, postTrust, promoted,
                    degradedConflictCount);
        return new PromotionOutcome(promoted, degradedConflictCount);
    }

    /**
     * Batch evaluate and promote semantic units through the full gate pipeline.
     * More efficient than sequential {@link #evaluateAndPromote} for many candidates
     * because dedup and trust scoring use batch LLM calls.
     */
    public int batchEvaluateAndPromote(String contextId, List<? extends SemanticUnit> units) {
        return batchEvaluateAndPromoteWithOutcome(contextId, units).promotedCount();
    }

    public PromotionOutcome batchEvaluateAndPromoteWithOutcome(String contextId, List<? extends SemanticUnit> units) {
        if (units.isEmpty()) {
            return PromotionOutcome.empty();
        }

        var threshold = properties.unit().autoActivateThreshold();
        var initialRank = properties.unit().initialRank();
        var maxBatch = properties.llmCall().batchMaxSize();

        // Extraction can produce identical text twice from one response; keep the first
        // occurrence so downstream Collectors.toMap() keyed by text never encounters a duplicate key.
        var seen = new java.util.LinkedHashSet<String>();
        var confident = units.stream()
                             .filter(u -> u.isPromotionCandidate() && u.confidence() >= threshold)
                             .filter(u -> seen.add(u.text()))
                             .limit(maxBatch)
                             .toList();

        if (confident.isEmpty()) {
            logger.info("Batch promotion: all {} candidates filtered at confidence gate", units.size());
            return PromotionOutcome.empty();
        }

        var existingMemoryUnits = engine.findByContext(contextId);

        var postPrecheck = shouldRunPrecheck()
                ? confident.stream()
                           .filter(u -> !isFilteredByPrecheck(u.text(), existingMemoryUnits))
                           .toList()
                : confident;

        if (postPrecheck.isEmpty()) {
            logger.info("Batch promotion: all candidates filtered at conflict pre-check gate");
            return PromotionOutcome.empty();
        }

        var existingTexts = existingMemoryUnits.stream()
                                               .map(MemoryUnit::text)
                                               .collect(Collectors.toSet());
        var postExistingDedup = postPrecheck.stream()
                                            .filter(u -> !existingTexts.contains(u.text()))
                                            .toList();

        if (postExistingDedup.isEmpty()) {
            logger.info("Batch promotion: all candidates filtered at existing-unit dedup gate");
            return PromotionOutcome.empty();
        }

        var candidateTexts = postExistingDedup.stream().map(SemanticUnit::text).toList();
        var dedupResults = batchDedupWithFallback(candidateTexts, existingMemoryUnits);
        var postDedup = postExistingDedup.stream()
                                         .filter(u -> !dedupResults.getOrDefault(u.text(), false))
                                         .toList();

        if (postDedup.isEmpty()) {
            logger.info("Batch promotion: all candidates filtered at dedup gate");
            return PromotionOutcome.empty();
        }

        var postDedupTexts = postDedup.stream().map(SemanticUnit::text).toList();
        var conflictResults = engine.batchDetectConflicts(contextId, postDedupTexts);
        var postConflict = new ArrayList<SemanticUnit>();
        var degradedConflictCount = 0;
        for (var unit : postDedup) {
            var conflicts = conflictResults.getOrDefault(unit.text(), List.of());
            if (conflicts.isEmpty()) {
                postConflict.add(unit);
                continue;
            }
            var resolutionResult = resolveConflicts(unit, conflicts);
            degradedConflictCount += resolutionResult.degradedConflictCount();
            if (resolutionResult.accepted()) {
                postConflict.add(unit);
            }
        }

        if (postConflict.isEmpty()) {
            logger.info("Batch promotion: all candidates filtered at conflict gate");
            return new PromotionOutcome(0, degradedConflictCount);
        }

        var trustContexts = postConflict.stream()
                                        .map(u -> repository.findPropositionNodeById(u.id())
                                                            .map(node -> new TrustContext(node, contextId))
                                                            .orElse(null))
                                        .filter(java.util.Objects::nonNull)
                                        .toList();

        var trustResults = trustPipeline.batchEvaluate(trustContexts);
        var postTrust = postConflict.stream()
                                    .filter(u -> {
                                        var score = trustResults.get(u.text());
                                        if (score == null) {
                                            return true; // no node found, allow (matches sequential behavior)
                                        }
                                        return score.promotionZone() == PromotionZone.AUTO_PROMOTE;
                                    })
                                    .toList();

        if (postTrust.isEmpty()) {
            logger.info("Batch promotion: all candidates filtered at trust gate");
            return new PromotionOutcome(0, degradedConflictCount);
        }

        int promoted = 0;
        for (var unit : postTrust) {
            var trustScore = trustResults.get(unit.text());
            if (trustScore != null) {
                engine.promote(unit.id(), initialRank, trustScore.authorityCeiling());
            } else {
                engine.promote(unit.id(), initialRank);
            }
            promoted++;
        }

        logger.info("Batch promotion funnel for context {}: {} input, {} post-confidence, {} post-precheck, " +
                    "{} post-dedup, {} post-conflict, {} post-trust, {} promoted, {} degraded-conflict(s)",
                    contextId, units.size(), confident.size(), postPrecheck.size(), postDedup.size(),
                    postConflict.size(), postTrust.size(), promoted, degradedConflictCount);
        return new PromotionOutcome(promoted, degradedConflictCount);
    }

    private boolean shouldRunPrecheck() {
        return conflictIndex != null && conflictIndex.size() > 0;
    }

    private boolean isFilteredByPrecheck(String propositionText, List<MemoryUnit> existingUnits) {
        for (var unit : existingUnits) {
            var conflicts = conflictIndex.getConflicts(unit.id());
            for (var entry : conflicts) {
                if (entry.unitText().equals(propositionText) && entry.authority().isAtLeast(Authority.RELIABLE)) {
                    logger.info("promotion.precheck.rejected unitId={} conflicting={} authority={} confidence={}",
                                unit.id(), propositionText.length() > 80
                                        ? propositionText.substring(0, 80) + "..."
                                        : propositionText,
                                entry.authority(), entry.confidence());
                    return true;
                }
            }
        }
        return false;
    }

    private Map<String, Boolean> batchDedupWithFallback(List<String> candidates,
                                                        List<MemoryUnit> units) {
        try {
            return duplicateDetector.batchIsDuplicate(candidates, units);
        } catch (Exception e) {
            logger.warn("Batch dedup failed, falling back to per-candidate: {}", e.getMessage());
            return candidates.stream()
                             .collect(Collectors.toMap(
                                     c -> c,
                                     c -> duplicateDetector.isDuplicate(c, units)));
        }
    }

    private ConflictResolutionResult resolveConflicts(SemanticUnit unit, List<ConflictDetector.Conflict> conflicts) {
        record Resolved(ConflictDetector.Conflict conflict, ConflictResolver.Resolution resolution) {}

        int degradedCount = 0;
        var resolved = new ArrayList<Resolved>();
        boolean rejected = false;

        var incomingSourceId = resolveIncomingSourceId(unit);

        for (var conflict : conflicts) {
            if (conflict.detectionQuality() == ConflictDetector.DetectionQuality.DEGRADED
                || conflict.existing() == null) {
                logger.warn("Degraded conflict detection for unit {} — routing to review; reason={}",
                            unit.id(), conflict.reason());
                degradedCount++;
                rejected = true;
                break;
            }

            var context = buildResolutionContext(incomingSourceId, conflict.existing());
            var resolution = engine.resolveConflict(conflict, context);
            resolved.add(new Resolved(conflict, resolution));
            logger.info("Conflict resolution for unit {} vs memory unit {}: {}",
                        unit.id(), conflict.existing().id(), resolution);

            if (resolution == ConflictResolver.Resolution.KEEP_EXISTING) {
                rejected = true;
                break;
            }
        }

        var outcomes = rejected && degradedCount > 0
                ? List.of("DEGRADED")
                : resolved.stream().map(r -> r.resolution().name()).toList();

        if (!rejected) {
            for (var r : resolved) {
                switch (r.resolution()) {
                    case REPLACE -> {
                        logger.info("Replacing memory unit {} with unit {}",
                                    r.conflict().existing().id(), unit.id());
                        var archiveReason = r.conflict().conflictType() == ConflictType.REVISION
                                ? ArchiveReason.REVISION
                                : ArchiveReason.CONFLICT_REPLACEMENT;
                        engine.supersede(r.conflict().existing().id(), unit.id(), archiveReason);
                    }
                    case DEMOTE_EXISTING -> {
                        logger.info("Demoting existing memory unit {} due to conflict with unit {}",
                                    r.conflict().existing().id(), unit.id());
                        engine.demote(r.conflict().existing().id(), DemotionReason.CONFLICT_EVIDENCE);
                        engine.reEvaluateTrust(r.conflict().existing().id());
                    }
                    case COEXIST -> {
                        logger.debug("Coexisting: unit {} and memory unit {}",
                                     unit.id(), r.conflict().existing().id());
                        engine.reEvaluateTrust(r.conflict().existing().id());
                    }
                    case KEEP_EXISTING -> throw new IllegalStateException("KEEP_EXISTING in pass 2");
                }
            }
        } else {
            for (var r : resolved) {
                if (r.resolution() == ConflictResolver.Resolution.KEEP_EXISTING) {
                    engine.reEvaluateTrust(r.conflict().existing().id());
                }
            }
        }

        var span = Span.current();
        span.setAttribute("conflict.detected_count", conflicts.size());
        span.setAttribute("conflict.resolved_count", resolved.size());
        span.setAttribute("conflict.resolution_outcomes", String.join(",", outcomes));
        span.setAttribute("conflict.degraded_count", degradedCount);

        if (rejected) {
            return ConflictResolutionResult.rejected(degradedCount);
        }
        return new ConflictResolutionResult(true, degradedCount);
    }

    private @Nullable String resolveIncomingSourceId(SemanticUnit unit) {
        return repository.findPropositionNodeById(unit.id())
                         .map(node -> node.getSourceIds().isEmpty() ? null : node.getSourceIds().getFirst())
                         .orElse(null);
    }

    private ResolutionContext buildResolutionContext(@Nullable String incomingSourceId,
                                                     MemoryUnit existing) {
        if (sourceAuthorityResolver == null || incomingSourceId == null || existing.sourceId() == null) {
            return ResolutionContext.NONE;
        }
        var relation = sourceAuthorityResolver.compare(incomingSourceId, existing.sourceId());
        return new ResolutionContext(incomingSourceId, relation);
    }
}
