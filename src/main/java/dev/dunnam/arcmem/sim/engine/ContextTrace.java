package dev.dunnam.diceanchors.sim.engine;

import dev.dunnam.diceanchors.anchor.Anchor;

import java.util.List;

/**
 * Captures what was injected into the LLM prompt for a single simulation turn,
 * plus any DICE extraction metadata when extraction is enabled.
 * <p>
 * Note: {@code assembledPrompt} contains the injected context blocks
 * (anchors and working propositions).
 * Use {@code fullSystemPrompt} and {@code fullUserPrompt} for the complete prompt.
 * <p>
 * Extraction fields default to zero/empty when extraction is not enabled for the scenario.
 */
public record ContextTrace(
        int turnNumber,
        int anchorTokens,
        int totalTokens,
        List<Anchor> injectedAnchors,
        boolean injectionEnabled,
        String assembledPrompt,
        String fullSystemPrompt,
        String fullUserPrompt,
        boolean budgetApplied,
        int anchorsExcluded,
        int propositionsExtracted,
        int propositionsPromoted,
        int degradedConflictCount,
        List<String> extractedTexts,
        int hotCount,
        int warmCount,
        int coldCount,
        ComplianceSnapshot complianceSnapshot,
        int injectionPatternsDetected,
        SweepSnapshot sweepSnapshot
) {
    /**
     * Convenience constructor for turns without extraction.
     */
    public ContextTrace(
            int turnNumber,
            int anchorTokens,
            int totalTokens,
            List<Anchor> injectedAnchors,
            boolean injectionEnabled,
            String assembledPrompt,
            String fullSystemPrompt,
            String fullUserPrompt) {
        this(turnNumber, anchorTokens, totalTokens, injectedAnchors, injectionEnabled,
             assembledPrompt, fullSystemPrompt, fullUserPrompt, false, 0, 0, 0, 0, List.of(), 0, 0, 0,
             ComplianceSnapshot.none(), 0, SweepSnapshot.none());
    }

    /**
     * Convenience constructor for turns without extraction and with explicit budget metadata.
     */
    public ContextTrace(
            int turnNumber,
            int anchorTokens,
            int totalTokens,
            List<Anchor> injectedAnchors,
            boolean injectionEnabled,
            String assembledPrompt,
            String fullSystemPrompt,
            String fullUserPrompt,
            boolean budgetApplied,
            int anchorsExcluded) {
        this(turnNumber, anchorTokens, totalTokens, injectedAnchors, injectionEnabled,
             assembledPrompt, fullSystemPrompt, fullUserPrompt, budgetApplied, anchorsExcluded, 0, 0, 0, List.of(), 0, 0, 0,
             ComplianceSnapshot.none(), 0, SweepSnapshot.none());
    }
}
