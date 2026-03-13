package dev.arcmem.core.memory.model;

import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionStatus;

/**
 * Adapter that wraps a DICE {@link Proposition} as a {@link SemanticUnit}.
 * Retains the original proposition for callers that need DICE-specific fields
 * (persistence layer can unwrap via {@link #proposition()} or pattern matching).
 */
public record PropositionSemanticUnit(Proposition proposition) implements SemanticUnit {

    @Override
    public String id() {
        return proposition.getId();
    }

    @Override
    public String text() {
        return proposition.getText();
    }

    @Override
    public double confidence() {
        return proposition.getConfidence();
    }

    @Override
    public boolean isPromotionCandidate() {
        var status = proposition.getStatus();
        return status == null || status == PropositionStatus.ACTIVE || status == PropositionStatus.PROMOTED;
    }
}
