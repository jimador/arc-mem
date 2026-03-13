package dev.arcmem.core.assembly.protection;

import dev.arcmem.core.memory.model.Authority;
import dev.arcmem.core.memory.model.MemoryUnit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds token-level constraints from a set of units using entity name biasing.
 * <p>
 * Only CANON and RELIABLE units produce constraints. Entity names are extracted
 * by identifying capitalized words that are not common stop words or verbs.
 * {@code translationCoverage} tracks the fraction of each unit's tokens that
 * became expressible constraints — a proxy for the logit bias effectiveness gap.
 */
public class SemanticUnitConstraintIndex {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "shall", "can", "to", "of", "in", "on",
            "at", "by", "for", "with", "from", "and", "or", "but", "not", "no",
            "it", "its", "this", "that", "these", "those", "he", "she", "they",
            "we", "i", "my", "his", "her", "their", "our", "your", "who", "which",
            "what", "when", "where", "how", "if", "as", "into", "about", "between",
            "after", "before", "during", "while", "because", "since", "although"
    );

    private final List<SemanticUnitConstraint> constraints;
    private final double totalCoverage;

    private SemanticUnitConstraintIndex(List<SemanticUnitConstraint> constraints) {
        this.constraints = List.copyOf(constraints);
        this.totalCoverage = constraints.isEmpty()
                ? 0.0
                : constraints.stream().mapToDouble(SemanticUnitConstraint::translationCoverage).average().orElse(0.0);
    }

    /**
     * Build an index from the given units, filtering to CANON and RELIABLE only.
     */
    public static SemanticUnitConstraintIndex build(List<MemoryUnit> units) {
        if (units == null || units.isEmpty()) {
            return new SemanticUnitConstraintIndex(List.of());
        }
        var constraints = new ArrayList<SemanticUnitConstraint>();
        for (var unit : units) {
            if (unit.authority() != Authority.CANON && unit.authority() != Authority.RELIABLE) {
                continue;
            }
            var constraint = buildConstraint(unit);
            if (!constraint.boostTokens().isEmpty()) {
                constraints.add(constraint);
            }
        }
        return new SemanticUnitConstraintIndex(constraints);
    }

    private static SemanticUnitConstraint buildConstraint(MemoryUnit unit) {
        var text = unit.text();
        var rawTokens = tokenize(text);
        var boostTokens = new HashSet<String>();
        for (var token : rawTokens) {
            if (isEntityCandidate(token)) {
                boostTokens.add(token);
            }
        }
        var coverage = rawTokens.isEmpty() ? 0.0 : (double) boostTokens.size() / rawTokens.size();
        return new SemanticUnitConstraint(unit.id(), unit.authority(), Set.copyOf(boostTokens), Set.of(), coverage);
    }

    /**
     * Split text on whitespace and punctuation boundaries, stripping punctuation from tokens.
     */
    static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        var parts = text.split("[\\s\\p{Punct}]+");
        var tokens = new ArrayList<String>();
        for (var part : parts) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    /**
     * A token is an entity candidate if it starts with an uppercase letter and
     * is not in the stop word list.
     */
    static boolean isEntityCandidate(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        if (!Character.isUpperCase(token.charAt(0))) {
            return false;
        }
        return !STOP_WORDS.contains(token.toLowerCase());
    }

    public List<SemanticUnitConstraint> getConstraints() {
        return constraints;
    }

    /**
     * Average translation coverage across all constraints in this index.
     */
    public double getTotalCoverage() {
        return totalCoverage;
    }
}
