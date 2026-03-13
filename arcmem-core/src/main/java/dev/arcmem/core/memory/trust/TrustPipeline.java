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

import dev.arcmem.core.persistence.PropositionNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Facade composing {@link TrustEvaluator} and zone routing.
 * Provides a single entry point for trust evaluation of propositions.
 * <p>
 * Uses the BALANCED domain profile by default. Call {@link #withProfile(DomainProfile)}
 * to create a pipeline instance with a different profile for simulation-time switching.
 */
public class TrustPipeline {

    private static final Logger logger = LoggerFactory.getLogger(TrustPipeline.class);

    private final List<TrustSignal> signals;
    private volatile DomainProfile activeProfile;

    public TrustPipeline(List<TrustSignal> signals) {
        this.signals = signals;
        this.activeProfile = DomainProfile.BALANCED;
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
     * Evaluate trust for a batch of propositions.
     * All current trust signals are non-LLM and computed per-proposition.
     *
     * @param contexts list of propositions with their context IDs
     * @return map from proposition text to trust score
     */
    public Map<String, TrustScore> batchEvaluate(List<TrustContext> contexts) {
        return contexts.stream()
                .collect(Collectors.toMap(
                        ctx -> ctx.proposition().getText(),
                        ctx -> evaluate(ctx.proposition(), ctx.contextId())));
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
