package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.persistence.PropositionNode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
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
@Component
public class GraphConsistencySignal implements TrustSignal {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "is", "are", "was", "were",
            "in", "on", "at", "to", "of", "and", "or",
            "for", "with", "that", "this", "has", "had", "have"
    );

    private final AnchorEngine anchorEngine;

    public GraphConsistencySignal(AnchorEngine anchorEngine) {
        this.anchorEngine = anchorEngine;
    }

    @Override
    public String name() {
        return "graphConsistency";
    }

    @Override
    public OptionalDouble evaluate(PropositionNode proposition, String contextId) {
        List<Anchor> anchors = anchorEngine.inject(contextId);
        if (anchors.isEmpty()) {
            return OptionalDouble.of(0.5);
        }

        var propositionWords = tokenize(proposition.getText());
        double maxSimilarity = 0.0;
        for (var anchor : anchors) {
            // Skip self-comparison
            if (anchor.id().equals(proposition.getId())) {
                continue;
            }
            var anchorWords = tokenize(anchor.text());
            double similarity = jaccardSimilarity(propositionWords, anchorWords);
            maxSimilarity = Math.max(maxSimilarity, similarity);
        }
        return OptionalDouble.of(maxSimilarity);
    }

    /**
     * Tokenize text into a set of lowercase words with stop words removed.
     */
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
        long intersectionSize = a.stream().filter(b::contains).count();
        long unionSize = a.size() + b.size() - intersectionSize;
        return unionSize == 0 ? 0.0 : (double) intersectionSize / unionSize;
    }
}
