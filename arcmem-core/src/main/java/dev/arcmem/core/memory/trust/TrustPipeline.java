package dev.arcmem.core.memory.trust;

import dev.arcmem.core.memory.model.Authority;
import dev.arcmem.core.memory.model.DomainProfile;
import dev.arcmem.core.memory.model.PromotionZone;
import dev.arcmem.core.persistence.PropositionNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Facade composing {@link TrustEvaluator} and zone routing.
 * Uses BALANCED domain profile by default; call {@link #withProfile(DomainProfile)} to switch.
 */
public class TrustPipeline {

    private static final Logger logger = LoggerFactory.getLogger(TrustPipeline.class);

    private final List<TrustSignal> signals;
    private volatile DomainProfile activeProfile;
    private volatile boolean enabled = true;

    public TrustPipeline(List<TrustSignal> signals) {
        this.signals = signals;
        this.activeProfile = DomainProfile.BALANCED;
    }

    public TrustScore evaluate(PropositionNode proposition, String contextId) {
        if (!enabled) {
            return new TrustScore(0.9, Authority.RELIABLE, PromotionZone.AUTO_PROMOTE, Map.of(), Instant.now());
        }
        var evaluator = new TrustEvaluator(signals, activeProfile);
        var score = evaluator.evaluate(proposition, contextId);
        logger.debug("Trust pipeline evaluated proposition {} — score={} zone={}",
                     proposition.getId(), score.score(), score.promotionZone());
        return score;
    }

    public Map<String, TrustScore> batchEvaluate(List<TrustContext> contexts) {
        if (!enabled) {
            return contexts.stream()
                           .collect(Collectors.toMap(
                                   ctx -> ctx.proposition().getText(),
                                   ctx -> new TrustScore(0.9, Authority.RELIABLE, PromotionZone.AUTO_PROMOTE, Map.of(), Instant.now())));
        }
        return contexts.stream()
                       .collect(Collectors.toMap(
                               ctx -> ctx.proposition().getText(),
                               ctx -> evaluate(ctx.proposition(), ctx.contextId())));
    }

    public TrustPipeline withProfile(DomainProfile profile) {
        this.activeProfile = profile;
        logger.info("Trust pipeline switched to profile: {}", profile.name());
        return this;
    }

    public DomainProfile getActiveProfile() {
        return activeProfile;
    }

    /**
     * Ablation hook: disables trust evaluation so the NO_TRUST experiment condition
     * can isolate trust's contribution. When disabled, all propositions pass through
     * at score 0.9 / AUTO_PROMOTE. Caller must restore via {@code setEnabled(true)}
     * after the experiment run.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            logger.info("Trust pipeline disabled — all propositions will pass through at score=0.9");
        }
    }

    /** Ablation hook: see {@link #setEnabled(boolean)}. */
    public boolean isEnabled() {
        return enabled;
    }
}
