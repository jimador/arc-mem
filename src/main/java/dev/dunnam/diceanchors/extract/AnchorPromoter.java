package dev.dunnam.diceanchors.extract;

import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionStatus;
import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.ConflictDetector;
import dev.dunnam.diceanchors.anchor.ConflictResolver;
import dev.dunnam.diceanchors.anchor.PromotionZone;
import dev.dunnam.diceanchors.anchor.TrustPipeline;
import dev.dunnam.diceanchors.anchor.event.ArchiveReason;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Evaluates DICE propositions for promotion to anchor status through a
 * multi-gate pipeline: confidence -> dedup -> conflict resolution -> trust -> promote.
 * <p>
 * Each gate filters or resolves propositions before they reach promotion.
 * Conflict resolution uses {@link ConflictResolver} to determine whether
 * conflicting anchors should be kept, replaced, or allowed to coexist.
 * <p>
 * Invariant P1: duplicate propositions never produce separate anchors.
 * Invariant P2: conflict resolution decisions are always acted upon (KEEP/REPLACE/COEXIST).
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

    /**
     * Evaluate a list of propositions for promotion through the full pipeline.
     * Pipeline gates: confidence -> dedup -> conflict resolution -> trust -> promote.
     *
     * @return number of propositions promoted
     */
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

            // Gate 1: Confidence threshold
            if (prop.getConfidence() < threshold) {
                logger.debug("Skipping proposition {} - confidence {} below threshold {}",
                        prop.getId(), prop.getConfidence(), threshold);
                continue;
            }
            postConfidence++;

            // Gate 2: Duplicate detection
            if (duplicateDetector.isDuplicate(contextId, prop.getText())) {
                logger.info("Proposition {} is duplicate, filtering at dedup gate", prop.getId());
                continue;
            }
            postDedup++;

            // Gate 3: Conflict detection and resolution
            var conflicts = engine.detectConflicts(contextId, prop.getText());
            if (!conflicts.isEmpty()) {
                if (!resolveConflicts(prop, conflicts)) {
                    continue;
                }
            }
            postConflict++;

            // Gate 4: Trust evaluation
            var node = repository.findPropositionNodeById(prop.getId());
            if (node != null) {
                logger.debug("Trust gate: proposition {} sourceIds={} confidence={}",
                        prop.getId(), node.getSourceIds(), node.getConfidence());
                var trustScore = trustPipeline.evaluate(node, contextId);
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

            engine.promote(prop.getId(), initialRank);
            promoted++;
        }

        logger.info("Promotion funnel for context {}: {} active, {} post-confidence, {} post-dedup, " +
                        "{} post-conflict, {} post-trust, {} promoted",
                contextId, total, postConfidence, postDedup, postConflict, postTrust, promoted);
        return promoted;
    }

    private boolean isPromotionCandidate(PropositionStatus status) {
        if (status == null) {
            return true;
        }
        return status == PropositionStatus.ACTIVE || status == PropositionStatus.PROMOTED;
    }

    /**
     * Resolve all conflicts for an incoming proposition.
     * KEEP_EXISTING takes precedence - if any conflict resolves to KEEP, the incoming
     * proposition is rejected. REPLACE archives the existing anchor. COEXIST allows both.
     *
     * @return true if the incoming proposition should proceed to trust evaluation
     */
    private boolean resolveConflicts(Proposition prop, List<ConflictDetector.Conflict> conflicts) {
        for (var conflict : conflicts) {
            var resolution = engine.resolveConflict(conflict);
            logger.info("Conflict resolution for proposition {} vs anchor {}: {}",
                    prop.getId(), conflict.existing().id(), resolution);

            switch (resolution) {
                case KEEP_EXISTING -> {
                    logger.info("Keeping existing anchor {}, rejecting proposition {}",
                            conflict.existing().id(), prop.getId());
                    return false;
                }
                case REPLACE -> {
                    logger.info("Replacing anchor {} with proposition {}",
                            conflict.existing().id(), prop.getId());
                    engine.archive(conflict.existing().id(), ArchiveReason.CONFLICT_REPLACEMENT);
                }
                case COEXIST -> logger.debug("Coexisting: proposition {} and anchor {}",
                        prop.getId(), conflict.existing().id());
            }
        }
        return true;
    }
}
