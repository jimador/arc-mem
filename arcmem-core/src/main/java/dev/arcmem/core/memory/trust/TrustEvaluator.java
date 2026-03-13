package dev.arcmem.core.memory.trust;

import dev.arcmem.core.memory.model.Authority;
import dev.arcmem.core.memory.model.DomainProfile;
import dev.arcmem.core.memory.model.PromotionZone;
import dev.arcmem.core.persistence.PropositionNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Computes composite trust scores from weighted signal evaluations.
 * <p>
 * Absent-signal redistribution: when a signal returns {@link OptionalDouble#empty()},
 * its weight is redistributed proportionally among the remaining present signals.
 * <p>
 * Authority ceiling mapping (invariant A3: CANON is never assigned):
 * <ul>
 *   <li>score >= 0.80 -> RELIABLE</li>
 *   <li>0.50 <= score < 0.80 -> UNRELIABLE</li>
 *   <li>score < 0.50 -> PROVISIONAL</li>
 * </ul>
 */
public class TrustEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(TrustEvaluator.class);

    private final List<TrustSignal> signals;
    private final DomainProfile profile;

    public TrustEvaluator(List<TrustSignal> signals, DomainProfile profile) {
        this.signals = signals;
        this.profile = profile;
    }

    /**
     * Evaluate all signals for a proposition and produce a composite TrustScore.
     *
     * @param proposition the proposition to evaluate
     * @param contextId   the conversation or session context
     *
     * @return the computed TrustScore with score, zone, ceiling, and audit
     */
    public TrustScore evaluate(PropositionNode proposition, String contextId) {
        Map<String, OptionalDouble> rawValues = new LinkedHashMap<>();
        for (var signal : signals) {
            var value = signal.evaluate(proposition, contextId);
            rawValues.put(signal.name(), value);
        }

        Map<String, Double> presentValues = new HashMap<>();
        double absentWeight = 0.0;
        double presentWeight = 0.0;

        for (var entry : rawValues.entrySet()) {
            double weight = profile.weights().getOrDefault(entry.getKey(), 0.0);
            if (entry.getValue().isPresent()) {
                presentValues.put(entry.getKey(), entry.getValue().getAsDouble());
                presentWeight += weight;
            } else {
                absentWeight += weight;
                logger.debug("Signal {} absent for proposition {}, redistributing weight {}",
                             entry.getKey(), proposition.getId(), weight);
            }
        }

        double score = 0.0;
        Map<String, Double> signalAudit = new LinkedHashMap<>();

        if (presentWeight > 0.0) {
            double redistributionFactor = (presentWeight + absentWeight) / presentWeight;
            for (var entry : presentValues.entrySet()) {
                double originalWeight = profile.weights().getOrDefault(entry.getKey(), 0.0);
                double adjustedWeight = originalWeight * redistributionFactor;
                double contribution = entry.getValue() * adjustedWeight;
                score += contribution;
                signalAudit.put(entry.getKey(), entry.getValue());
            }
        }

        score = Math.max(0.0, Math.min(1.0, score));

        var zone = routeToZone(score);
        var ceiling = deriveAuthorityCeiling(score);

        logger.debug("Trust score for proposition {}: {} zone={} ceiling={}",
                     proposition.getId(), score, zone, ceiling);

        return new TrustScore(score, ceiling, zone, Map.copyOf(signalAudit), Instant.now());
    }

    private PromotionZone routeToZone(double score) {
        if (score >= profile.autoPromoteThreshold()) {
            return PromotionZone.AUTO_PROMOTE;
        } else if (score >= profile.reviewThreshold()) {
            return PromotionZone.REVIEW;
        } else {
            return PromotionZone.ARCHIVE;
        }
    }

    private Authority deriveAuthorityCeiling(double score) {
        if (score >= 0.80) {
            return Authority.RELIABLE;
        } else if (score >= 0.50) {
            return Authority.UNRELIABLE;
        } else {
            return Authority.PROVISIONAL;
        }
    }
}
