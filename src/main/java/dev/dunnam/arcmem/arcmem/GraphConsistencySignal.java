package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.persistence.PropositionNode;

import java.util.Arrays;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Trust signal that measures word-overlap similarity between a proposition
 * and existing active anchors in the same context.
 * <p>
 * Uses Jaccard similarity of word sets (with stop word removal) to compare
 * proposition text against each active anchor. Returns 0.5 (neutral) when no
 * anchors exist yet (cold-start scenario).
 */
public record GraphConsistencySignal(AnchorEngine anchorEngine) implements TrustSignal {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "is", "are", "was", "were",
            "in", "on", "at", "to", "of", "and", "or",
            "for", "with", "that", "this", "has", "had", "have"
    );

    @Override
    public String name() {
        return "graphConsistency";
    }

    @Override
    public OptionalDouble evaluate(PropositionNode proposition, String contextId) {
        var anchors = anchorEngine.inject(contextId);
        if (anchors.isEmpty()) {
            return OptionalDouble.of(0.5);
        }
        var propositionWords = tokenize(proposition.getText());
        var maxSimilarity = 0.0;
        for (var anchor : anchors) {
            if (anchor.id().equals(proposition.getId())) {
                continue;
            }
            maxSimilarity = Math.max(maxSimilarity, jaccardSimilarity(propositionWords, tokenize(anchor.text())));
        }
        return OptionalDouble.of(maxSimilarity);
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase().split("\\s+"))
                     .filter(w -> !w.isBlank())
                     .filter(w -> !STOP_WORDS.contains(w))
                     .collect(Collectors.toSet());
    }

    private double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0;
        }
        var intersectionSize = a.stream().filter(b::contains).count();
        var unionSize = a.size() + b.size() - intersectionSize;
        return unionSize == 0 ? 0.0 : (double) intersectionSize / unionSize;
    }
}
