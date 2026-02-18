package dev.dunnam.diceanchors.anchor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects conflicts between an incoming statement and existing anchors by identifying
 * negation patterns. If one statement contains negation markers and the other does not,
 * and they share significant lexical overlap, they are considered conflicting.
 */
public class NegationConflictDetector implements ConflictDetector {

    private static final Set<String> NEGATION_MARKERS = Set.of(
            "not", "never", "no longer", "isn't", "doesn't", "wasn't",
            "weren't", "hasn't", "haven't", "cannot", "can't", "won't",
            "didn't", "don't", "neither", "nor", "false"
    );

    @Override
    public List<Conflict> detect(String incomingText, List<Anchor> existingAnchors) {
        var conflicts = new ArrayList<Conflict>();
        var incomingLower = incomingText.toLowerCase();
        var incomingHasNegation = containsNegation(incomingLower);

        for (var anchor : existingAnchors) {
            var anchorLower = anchor.text().toLowerCase();
            var anchorHasNegation = containsNegation(anchorLower);

            if (incomingHasNegation != anchorHasNegation) {
                var overlap = calculateOverlap(incomingLower, anchorLower);
                if (overlap > 0.5) {
                    conflicts.add(new Conflict(
                            anchor,
                            incomingText,
                            overlap,
                            "Negation conflict: one statement negates the other"
                    ));
                }
            }
        }
        return conflicts;
    }

    private boolean containsNegation(String text) {
        for (var marker : NEGATION_MARKERS) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private double calculateOverlap(String a, String b) {
        var wordsA = Set.of(a.replaceAll("[^a-z0-9 ]", "").split("\\s+"));
        var wordsB = Set.of(b.replaceAll("[^a-z0-9 ]", "").split("\\s+"));

        var cleanA = new HashSet<>(wordsA);
        cleanA.removeAll(NEGATION_MARKERS);
        var cleanB = new HashSet<>(wordsB);
        cleanB.removeAll(NEGATION_MARKERS);

        if (cleanA.isEmpty() || cleanB.isEmpty()) {
            return 0.0;
        }

        var intersection = new HashSet<>(cleanA);
        intersection.retainAll(cleanB);
        var union = new HashSet<>(cleanA);
        union.addAll(cleanB);
        return (double) intersection.size() / union.size();
    }
}
