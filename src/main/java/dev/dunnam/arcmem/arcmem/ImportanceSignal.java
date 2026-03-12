package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.persistence.PropositionNode;

import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Trust signal that scores how relevant a proposition is to the current conversation context.
 * <p>
 * Importance = |proposition tokens ∩ context tokens| / |proposition tokens|, where context
 * tokens are the union of all active anchor texts. Returns 0.5 (neutral) when no anchors
 * exist. Returns {@link OptionalDouble#empty()} when quality scoring is disabled.
 */
public record ImportanceSignal(AnchorEngine anchorEngine, DiceAnchorsProperties properties) implements TrustSignal {

    @Override
    public String name() {
        return "importance";
    }

    @Override
    public OptionalDouble evaluate(PropositionNode proposition, String contextId) {
        try {
            var qualityConfig = properties.anchor().qualityScoring();
            if (qualityConfig == null || !qualityConfig.enabled()) {
                return OptionalDouble.empty();
            }

            var anchors = anchorEngine.inject(contextId);
            if (anchors.isEmpty()) {
                return OptionalDouble.of(0.5);
            }

            var propositionTokens = NoveltySignal.tokenize(proposition.getText());
            if (propositionTokens.isEmpty()) {
                return OptionalDouble.of(0.0);
            }

            Set<String> contextTokens = anchors.stream()
                    .flatMap(anchor -> NoveltySignal.tokenize(anchor.text()).stream())
                    .collect(Collectors.toSet());

            long overlap = propositionTokens.stream().filter(contextTokens::contains).count();
            double importance = (double) overlap / propositionTokens.size();

            return OptionalDouble.of(Math.max(0.0, Math.min(1.0, importance)));
        } catch (Exception e) {
            return OptionalDouble.empty();
        }
    }
}
