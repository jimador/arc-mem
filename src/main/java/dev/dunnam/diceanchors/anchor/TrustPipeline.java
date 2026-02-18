package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.persistence.PropositionNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Facade composing {@link TrustEvaluator} and zone routing.
 * Provides a single entry point for trust evaluation of propositions.
 * <p>
 * Uses the NARRATIVE domain profile by default. Call {@link #withProfile(DomainProfile)}
 * to create a pipeline instance with a different profile for simulation-time switching.
 */
@Service
public class TrustPipeline {

    private static final Logger logger = LoggerFactory.getLogger(TrustPipeline.class);

    private final List<TrustSignal> signals;
    private volatile DomainProfile activeProfile;

    public TrustPipeline(List<TrustSignal> signals) {
        this.signals = signals;
        this.activeProfile = DomainProfile.NARRATIVE;
    }

    /**
     * Evaluate trust for a proposition within a context.
     *
     * @param proposition the proposition node to evaluate
     * @param contextId   the conversation or session context
     *
     * @return the computed TrustScore
     */
    public TrustScore evaluate(PropositionNode proposition, String contextId) {
        var evaluator = new TrustEvaluator(signals, activeProfile);
        var score = evaluator.evaluate(proposition, contextId);
        logger.debug("Trust pipeline evaluated proposition {} — score={} zone={}",
                     proposition.getId(), score.score(), score.promotionZone());
        return score;
    }

    /**
     * Switch the active domain profile for subsequent evaluations.
     * Intended for simulation-time profile switching.
     *
     * @param profile the domain profile to use
     *
     * @return this pipeline instance for fluent usage
     */
    public TrustPipeline withProfile(DomainProfile profile) {
        this.activeProfile = profile;
        logger.info("Trust pipeline switched to profile: {}", profile.name());
        return this;
    }

    /**
     * Returns the currently active domain profile.
     */
    public DomainProfile getActiveProfile() {
        return activeProfile;
    }
}
