package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.persistence.PropositionNode;
import org.springframework.stereotype.Component;

import java.util.OptionalDouble;

/**
 * Trust signal that passes through the DICE extraction confidence score.
 * The confidence value is already in [0.0, 1.0].
 */
@Component
public class ExtractionConfidenceSignal implements TrustSignal {

    @Override
    public String name() {
        return "extractionConfidence";
    }

    @Override
    public OptionalDouble evaluate(PropositionNode proposition, String contextId) {
        return OptionalDouble.of(proposition.getConfidence());
    }
}
