package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.Authority;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds token-level constraints from a set of anchors using entity name biasing.
 * <p>
 * Only CANON and RELIABLE anchors produce constraints. Entity names are extracted
 * by identifying capitalized words that are not common stop words or verbs.
 * {@code translationCoverage} tracks the fraction of each anchor's tokens that
 * became expressible constraints — a proxy for the logit bias effectiveness gap.
 */
public class AnchorConstraintIndex {

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

    private final List<AnchorConstraint> constraints;
    private final double totalCoverage;

    private AnchorConstraintIndex(List<AnchorConstraint> constraints) {
        this.constraints = List.copyOf(constraints);
        this.totalCoverage = constraints.isEmpty()
                ? 0.0
                : constraints.stream().mapToDouble(AnchorConstraint::translationCoverage).average().orElse(0.0);
    }

    /**
     * Build an index from the given anchors, filtering to CANON and RELIABLE only.
     */
    public static AnchorConstraintIndex build(List<Anchor> anchors) {
        if (anchors == null || anchors.isEmpty()) {
            return new AnchorConstraintIndex(List.of());
        }
        var constraints = new ArrayList<AnchorConstraint>();
        for (var anchor : anchors) {
            if (anchor.authority() != Authority.CANON && anchor.authority() != Authority.RELIABLE) {
                continue;
            }
            var constraint = buildConstraint(anchor);
            if (!constraint.boostTokens().isEmpty()) {
                constraints.add(constraint);
            }
        }
        return new AnchorConstraintIndex(constraints);
    }

    private static AnchorConstraint buildConstraint(Anchor anchor) {
        var text = anchor.text();
        var rawTokens = tokenize(text);
        var boostTokens = new HashSet<String>();
        for (var token : rawTokens) {
            if (isEntityCandidate(token)) {
                boostTokens.add(token);
            }
        }
        var coverage = rawTokens.isEmpty() ? 0.0 : (double) boostTokens.size() / rawTokens.size();
        return new AnchorConstraint(anchor.id(), anchor.authority(), Set.copyOf(boostTokens), Set.of(), coverage);
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

    public List<AnchorConstraint> getConstraints() {
        return constraints;
    }

    /** Average translation coverage across all constraints in this index. */
    public double getTotalCoverage() {
        return totalCoverage;
    }
}
