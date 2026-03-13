package dev.arcmem.core.memory.trust;
import dev.arcmem.core.memory.budget.*;
import dev.arcmem.core.memory.canon.*;
import dev.arcmem.core.memory.conflict.*;
import dev.arcmem.core.memory.engine.*;
import dev.arcmem.core.memory.maintenance.*;
import dev.arcmem.core.memory.model.*;
import dev.arcmem.core.memory.mutation.*;
import dev.arcmem.core.memory.trust.*;
import dev.arcmem.core.assembly.budget.*;
import dev.arcmem.core.assembly.compaction.*;
import dev.arcmem.core.assembly.compliance.*;
import dev.arcmem.core.assembly.protection.*;
import dev.arcmem.core.assembly.retrieval.*;

import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.core.persistence.PropositionNode;

import java.util.Arrays;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Trust signal that scores how novel a proposition is relative to existing active units.
 * <p>
 * Novelty = 1.0 - maxJaccardSimilarity across all active units. A proposition
 * identical to an existing unit scores 0.0; one sharing no tokens scores 1.0.
 * Returns {@link OptionalDouble#empty()} when quality scoring is disabled, causing
 * {@link TrustEvaluator} to redistribute its weight to present signals.
 */
public record NoveltySignal(ArcMemEngine arcMemEngine, ArcMemProperties properties) implements TrustSignal {

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
            var qualityConfig = properties.unit().qualityScoring();
            if (qualityConfig == null || !qualityConfig.enabled()) {
                return OptionalDouble.empty();
            }

            var units = arcMemEngine.inject(contextId);
            if (units.isEmpty()) {
                return OptionalDouble.of(1.0);
            }

            var propositionTokens = tokenize(proposition.getText());
            var maxSimilarity = units.stream()
                    .mapToDouble(unit -> jaccardSimilarity(propositionTokens, tokenize(unit.text())))
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
