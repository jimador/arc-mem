package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.persistence.PropositionNode;

import java.util.Arrays;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Trust signal that scores how novel a proposition is relative to existing active anchors.
 * <p>
 * Novelty = 1.0 - maxJaccardSimilarity across all active anchors. A proposition
 * identical to an existing anchor scores 0.0; one sharing no tokens scores 1.0.
 * Returns {@link OptionalDouble#empty()} when quality scoring is disabled, causing
 * {@link TrustEvaluator} to redistribute its weight to present signals.
 */
public record NoveltySignal(AnchorEngine anchorEngine, DiceAnchorsProperties properties) implements TrustSignal {

    static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "is", "are", "was", "were",
            "in", "on", "at", "to", "of", "and", "or",
            "for", "with", "that", "this", "has", "had", "have",
            "it", "as", "be", "but", "not", "by", "from", "its"
    );

    @Override
    public String name() {
        return "novelty";
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
                return OptionalDouble.of(1.0);
            }

            var propositionTokens = tokenize(proposition.getText());
            var maxSimilarity = anchors.stream()
                    .mapToDouble(anchor -> jaccardSimilarity(propositionTokens, tokenize(anchor.text())))
                    .max()
                    .orElse(0.0);

            return OptionalDouble.of(Math.max(0.0, Math.min(1.0, 1.0 - maxSimilarity)));
        } catch (Exception e) {
            return OptionalDouble.empty();
        }
    }

    static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase().split("\\W+"))
                .filter(w -> w.length() > 2)
                .filter(w -> !STOP_WORDS.contains(w))
                .collect(Collectors.toSet());
    }

    static double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0;
        }
        var intersectionSize = a.stream().filter(b::contains).count();
        var unionSize = (long) a.size() + b.size() - intersectionSize;
        return unionSize == 0 ? 0.0 : (double) intersectionSize / unionSize;
    }
}
