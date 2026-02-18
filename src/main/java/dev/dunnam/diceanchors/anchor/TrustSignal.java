package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.persistence.PropositionNode;

import java.util.OptionalDouble;

/**
 * SPI for trust signal evaluation.
 * Each implementation contributes a single signal dimension to the
 * composite trust score computed by {@link TrustEvaluator}.
 * <p>
 * Returning {@link OptionalDouble#empty()} indicates the signal is
 * not applicable; its weight will be redistributed to present signals.
 */
public interface TrustSignal {

    /**
     * Unique name for this signal, used as key in the signal audit map
     * and in {@link DomainProfile} weight configurations.
     */
    String name();

    /**
     * Evaluate the trust signal for a proposition within a context.
     *
     * @param proposition the proposition node to evaluate
     * @param contextId   the conversation or session context
     *
     * @return signal value in [0.0, 1.0], or empty if not applicable
     */
    OptionalDouble evaluate(PropositionNode proposition, String contextId);
}
