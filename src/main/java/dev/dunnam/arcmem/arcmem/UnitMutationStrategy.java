package dev.dunnam.diceanchors.anchor;

public interface AnchorMutationStrategy {

    MutationDecision evaluate(MutationRequest request);
}
