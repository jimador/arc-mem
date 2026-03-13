package dev.arcmem.core.memory.trust;

import dev.arcmem.core.memory.engine.ArcMemEngine;
import dev.arcmem.core.persistence.PropositionNode;

import java.util.Arrays;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Trust signal that measures word-overlap similarity between a proposition
 * and existing active units in the same context.
 * <p>
 * Uses Jaccard similarity of word sets (with stop word removal) to compare
 * proposition text against each active unit. Returns 0.5 (neutral) when no
 * units exist yet (cold-start scenario).
 */
public record GraphConsistencySignal(ArcMemEngine arcMemEngine) implements TrustSignal {

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
        var units = arcMemEngine.inject(contextId);
        if (units.isEmpty()) {
            return OptionalDouble.of(0.5);
        }
        var propositionWords = tokenize(proposition.getText());
        var maxSimilarity = 0.0;
        for (var unit : units) {
            if (unit.id().equals(proposition.getId())) {
                continue;
            }
            maxSimilarity = Math.max(maxSimilarity, jaccardSimilarity(propositionWords, tokenize(unit.text())));
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
