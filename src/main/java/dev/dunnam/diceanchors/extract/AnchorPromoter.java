package dev.dunnam.diceanchors.extract;

import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionStatus;
import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.ConflictDetector;
import dev.dunnam.diceanchors.anchor.ConflictResolver;
import dev.dunnam.diceanchors.anchor.DemotionReason;
import dev.dunnam.diceanchors.anchor.PromotionZone;
import dev.dunnam.diceanchors.anchor.TrustContext;
import dev.dunnam.diceanchors.anchor.TrustPipeline;
import dev.dunnam.diceanchors.anchor.TrustScore;
import dev.dunnam.diceanchors.anchor.event.ArchiveReason;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Evaluates DICE propositions for promotion to anchor status through a
 * multi-gate pipeline.
 *
 * <h2>Pipeline Gates</h2>
 * <ol>
 *   <li><strong>Confidence gate</strong> — drops propositions below the configured
 *       {@code autoActivateThreshold}. Fast, no I/O.</li>
 *   <li><strong>Dedup gate</strong> — rejects propositions that are semantically
 *       identical to existing active anchors. Uses {@link DuplicateDetector} which
 *       combines fast normalized-string matching with optional LLM verification.
 *       In batch mode, an exact-text cross-reference against existing anchors runs
 *       first (no LLM call), followed by intra-batch dedup, before the LLM batch
 *       dedup call.</li>
 *   <li><strong>Conflict gate</strong> — detects semantic contradictions with existing
 *       anchors and applies {@link ConflictResolver} to decide the outcome.
 *       KEEP_EXISTING rejects the incoming proposition. REPLACE archives the existing
 *       anchor. DEMOTE_EXISTING demotes the existing anchor one authority level.
 *       COEXIST allows both. After resolution, trust is re-evaluated for any surviving
 *       existing anchor (KEEP_EXISTING, DEMOTE_EXISTING, COEXIST outcomes).</li>
 *   <li><strong>Trust gate</strong> — runs the {@link TrustPipeline} on each candidate.
 *       Propositions in the REVIEW or ARCHIVE zone are filtered out. The trust score's
 *       {@link TrustScore#authorityCeiling()} is passed to
 *       {@link AnchorEngine#promote(String, int, Authority)} so the initial authority
 *       ceiling is set at promotion time.</li>
 *   <li><strong>Promote</strong> — calls {@link AnchorEngine#promote} for each surviving
 *       proposition. Budget enforcement (eviction of lowest-ranked non-pinned anchors)
 *       is applied per-promote call.</li>
 * </ol>
 *
 * <h2>Batch vs. Sequential Processing</h2>
 * {@link #batchEvaluateAndPromote} processes all candidates together through Gates 1–4
 * using batch LLM calls, then promotes sequentially to preserve budget enforcement.
 * {@link #evaluateAndPromote} processes each proposition independently through the full
 * gate sequence. Both methods produce the same result for a single proposition; batch
 * processing is more efficient for many candidates.
 *
 * <h2>Relationship to AnchorEngine</h2>
 * {@code AnchorPromoter} orchestrates the promotion pipeline — it decides whether and
 * how to promote. {@link AnchorEngine} executes anchor lifecycle operations
 * (promote, archive, demote, reinforce). The promoter calls the engine; the engine
 * never calls back into the promoter.
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li><strong>P1</strong>: Duplicate propositions MUST NOT produce separate anchors.
 *       The dedup gate (both LLM and exact-text cross-reference) enforces this.</li>
 *   <li><strong>P2</strong>: Conflict resolution decisions MUST always be acted upon.
 *       Every non-null resolution outcome changes anchor or proposition state.</li>
 *   <li><strong>P3</strong>: Gate sequence is preserved — confidence → dedup → conflict
 *       → trust → promote. No gate is skipped or reordered.</li>
 *   <li><strong>P4</strong>: Budget enforcement is applied per {@link AnchorEngine#promote}
 *       call, not at the batch level. Batch size is capped at the configured maximum.</li>
 * </ul>
 */
@Service
public class AnchorPromoter {

    private static final Logger logger = LoggerFactory.getLogger(AnchorPromoter.class);

    private final AnchorEngine engine;
    private final DiceAnchorsProperties properties;
    private final TrustPipeline trustPipeline;
    private final AnchorRepository repository;
    private final DuplicateDetector duplicateDetector;

    public AnchorPromoter(AnchorEngine engine, DiceAnchorsProperties properties,
                          TrustPipeline trustPipeline, AnchorRepository repository,
                          DuplicateDetector duplicateDetector) {
        this.engine = engine;
        this.properties = properties;
        this.trustPipeline = trustPipeline;
        this.repository = repository;
        this.duplicateDetector = duplicateDetector;
    }

    public int evaluateAndPromote(String contextId, List<Proposition> propositions) {
        int total = 0;
        int postConfidence = 0;
        int postDedup = 0;
        int postConflict = 0;
        int postTrust = 0;
        int promoted = 0;

        var threshold = properties.anchor().autoActivateThreshold();
        var initialRank = properties.anchor().initialRank();

        for (var prop : propositions) {
            if (!isPromotionCandidate(prop.getStatus())) {
                continue;
            }
            total++;

            if (prop.getConfidence() < threshold) {
                logger.debug("Skipping proposition {} - confidence {} below threshold {}",
                        prop.getId(), prop.getConfidence(), threshold);
                continue;
            }
            postConfidence++;

            if (duplicateDetector.isDuplicate(contextId, prop.getText())) {
                logger.info("Proposition {} is duplicate, filtering at dedup gate", prop.getId());
                continue;
            }
            postDedup++;

            var conflicts = engine.detectConflicts(contextId, prop.getText());
            if (!conflicts.isEmpty()) {
                if (!resolveConflicts(prop, conflicts)) {
                    continue;
                }
            }
            postConflict++;

            TrustScore trustScore = null;
            var nodeOpt = repository.findPropositionNodeById(prop.getId());
            if (nodeOpt.isPresent()) {
                var node = nodeOpt.get();
                logger.debug("Trust gate: proposition {} sourceIds={} confidence={}",
                        prop.getId(), node.getSourceIds(), node.getConfidence());
                trustScore = trustPipeline.evaluate(node, contextId);
                logger.info("Trust gate: proposition {} score={} zone={} audit={}",
                        prop.getId(), trustScore.score(), trustScore.promotionZone(),
                        trustScore.signalAudit());
                if (trustScore.promotionZone() == PromotionZone.REVIEW) {
                    logger.info("Proposition {} in REVIEW zone (score={}), skipping auto-promotion",
                            prop.getId(), trustScore.score());
                    continue;
                }
                if (trustScore.promotionZone() == PromotionZone.ARCHIVE) {
                    logger.info("Proposition {} in ARCHIVE zone (score={}), skipping",
                            prop.getId(), trustScore.score());
                    continue;
                }
            } else {
                logger.warn("Trust gate: proposition {} not found in repository, skipping trust evaluation",
                        prop.getId());
            }
            postTrust++;

            if (trustScore != null) {
                engine.promote(prop.getId(), initialRank, trustScore.authorityCeiling());
            } else {
                engine.promote(prop.getId(), initialRank);
            }
            promoted++;
        }

        logger.info("Promotion funnel for context {}: {} active, {} post-confidence, {} post-dedup, " +
                        "{} post-conflict, {} post-trust, {} promoted",
                contextId, total, postConfidence, postDedup, postConflict, postTrust, promoted);
        return promoted;
    }

    /**
     * Batch evaluate and promote propositions through the full gate pipeline.
     * More efficient than sequential {@link #evaluateAndPromote} for many candidates
     * because dedup and trust scoring use batch LLM calls.
     */
    public int batchEvaluateAndPromote(String contextId, List<Proposition> propositions) {
        if (propositions.isEmpty()) {
            return 0;
        }

        var threshold = properties.anchor().autoActivateThreshold();
        var initialRank = properties.anchor().initialRank();
        var maxBatch = properties.sim().batchMaxSize();

        // DICE can extract identical text twice from one response; keep the first occurrence
        // so downstream Collectors.toMap() keyed by text never encounters a duplicate key.
        var seen = new java.util.LinkedHashSet<String>();
        var confident = propositions.stream()
                .filter(p -> isPromotionCandidate(p.getStatus()) && p.getConfidence() >= threshold)
                .filter(p -> seen.add(p.getText()))
                .limit(maxBatch)
                .toList();

        if (confident.isEmpty()) {
            logger.info("Batch promotion: all {} candidates filtered at confidence gate", propositions.size());
            return 0;
        }

        var existingTexts = engine.findByContext(contextId).stream()
                .map(dev.dunnam.diceanchors.anchor.Anchor::text)
                .collect(Collectors.toSet());
        var postExistingDedup = confident.stream()
                .filter(p -> !existingTexts.contains(p.getText()))
                .toList();

        if (postExistingDedup.isEmpty()) {
            logger.info("Batch promotion: all candidates filtered at existing-anchor dedup gate");
            return 0;
        }

        var candidateTexts = postExistingDedup.stream().map(Proposition::getText).toList();
        var dedupResults = batchDedupWithFallback(contextId, candidateTexts);
        var postDedup = postExistingDedup.stream()
                .filter(p -> !dedupResults.getOrDefault(p.getText(), false))
                .toList();

        if (postDedup.isEmpty()) {
            logger.info("Batch promotion: all candidates filtered at dedup gate");
            return 0;
        }

        var postDedupTexts = postDedup.stream().map(Proposition::getText).toList();
        var conflictResults = engine.batchDetectConflicts(contextId, postDedupTexts);
        var postConflict = new ArrayList<Proposition>();
        for (var prop : postDedup) {
            var conflicts = conflictResults.getOrDefault(prop.getText(), List.of());
            if (conflicts.isEmpty() || resolveConflicts(prop, conflicts)) {
                postConflict.add(prop);
            }
        }

        if (postConflict.isEmpty()) {
            logger.info("Batch promotion: all candidates filtered at conflict gate");
            return 0;
        }

        var trustContexts = postConflict.stream()
                .map(p -> repository.findPropositionNodeById(p.getId())
                        .map(node -> new TrustContext(node, contextId))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();

        var trustResults = trustPipeline.batchEvaluate(trustContexts);
        var postTrust = postConflict.stream()
                .filter(p -> {
                    var score = trustResults.get(p.getText());
                    if (score == null) return true; // no node found, allow (matches sequential behavior)
                    return score.promotionZone() == PromotionZone.AUTO_PROMOTE;
                })
                .toList();

        if (postTrust.isEmpty()) {
            logger.info("Batch promotion: all candidates filtered at trust gate");
            return 0;
        }

        int promoted = 0;
        for (var prop : postTrust) {
            var trustScore = trustResults.get(prop.getText());
            if (trustScore != null) {
                engine.promote(prop.getId(), initialRank, trustScore.authorityCeiling());
            } else {
                engine.promote(prop.getId(), initialRank);
            }
            promoted++;
        }

        logger.info("Batch promotion funnel for context {}: {} input, {} post-confidence, {} post-dedup, " +
                        "{} post-conflict, {} post-trust, {} promoted",
                contextId, propositions.size(), confident.size(), postDedup.size(),
                postConflict.size(), postTrust.size(), promoted);
        return promoted;
    }

    private Map<String, Boolean> batchDedupWithFallback(String contextId, List<String> candidates) {
        try {
            return duplicateDetector.batchIsDuplicate(contextId, candidates);
        } catch (Exception e) {
            logger.warn("Batch dedup failed, falling back to per-candidate: {}", e.getMessage());
            return candidates.stream()
                    .collect(Collectors.toMap(
                            c -> c,
                            c -> duplicateDetector.isDuplicate(contextId, c)));
        }
    }

    private boolean isPromotionCandidate(PropositionStatus status) {
        if (status == null) {
            return true;
        }
        return status == PropositionStatus.ACTIVE || status == PropositionStatus.PROMOTED;
    }

    /**
     * Resolve all conflicts for an incoming proposition.
     * KEEP_EXISTING takes precedence — if any conflict resolves to KEEP, the incoming
     * proposition is rejected. REPLACE archives the existing anchor. DEMOTE_EXISTING
     * demotes the existing anchor one level and allows the incoming to proceed.
     * COEXIST allows both.
     * <p>
     * After any resolution that leaves an existing anchor in place (KEEP_EXISTING,
     * DEMOTE_EXISTING, COEXIST), trust is re-evaluated for the surviving existing anchor
     * (invariant P2: conflict decisions are always acted upon).
     *
     * @return true if the incoming proposition should proceed to trust evaluation
     */
    private boolean resolveConflicts(Proposition prop, List<ConflictDetector.Conflict> conflicts) {
        int resolvedCount = 0;
        var outcomes = new ArrayList<String>();
        boolean rejected = false;

        for (var conflict : conflicts) {
            var resolution = engine.resolveConflict(conflict);
            resolvedCount++;
            outcomes.add(resolution.name());
            logger.info("Conflict resolution for proposition {} vs anchor {}: {}",
                    prop.getId(), conflict.existing().id(), resolution);

            switch (resolution) {
                case KEEP_EXISTING -> {
                    logger.info("Keeping existing anchor {}, rejecting proposition {}",
                            conflict.existing().id(), prop.getId());
                    engine.reEvaluateTrust(conflict.existing().id());
                    rejected = true;
                }
                case REPLACE -> {
                    logger.info("Replacing anchor {} with proposition {}",
                            conflict.existing().id(), prop.getId());
                    engine.supersede(conflict.existing().id(), prop.getId(), ArchiveReason.CONFLICT_REPLACEMENT);
                }
                case DEMOTE_EXISTING -> {
                    logger.info("Demoting existing anchor {} due to conflict with proposition {}",
                            conflict.existing().id(), prop.getId());
                    engine.demote(conflict.existing().id(), DemotionReason.CONFLICT_EVIDENCE);
                    engine.reEvaluateTrust(conflict.existing().id());
                }
                case COEXIST -> {
                    logger.debug("Coexisting: proposition {} and anchor {}",
                            prop.getId(), conflict.existing().id());
                    engine.reEvaluateTrust(conflict.existing().id());
                }
            }

            if (rejected) {
                break;
            }
        }

        var span = Span.current();
        span.setAttribute("conflict.detected_count", conflicts.size());
        span.setAttribute("conflict.resolved_count", resolvedCount);
        span.setAttribute("conflict.resolution_outcomes", String.join(",", outcomes));

        return !rejected;
    }
}
