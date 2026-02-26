package dev.dunnam.diceanchors.anchor;

public record MutationRequest(
        String anchorId,
        String revisedText,
        MutationSource source,
        String requesterId
) {}
