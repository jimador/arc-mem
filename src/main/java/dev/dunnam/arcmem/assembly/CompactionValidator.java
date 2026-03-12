package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Anchor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Validates compaction summaries to ensure protected anchors survive summarization.
 * <p>
 * Pure utility class with no Spring dependencies. Checks whether the majority
 * (&gt;50%) of significant words from each protected anchor appear in the summary text.
 */
public final class CompactionValidator {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "is", "are", "was", "were",
            "in", "on", "at", "to", "of", "and", "or",
            "for", "with", "that", "this", "has", "had", "have"
    );

    private CompactionValidator() {}

    /**
     * Validates that protected anchors are represented in the compaction summary.
     * An anchor is considered "found" if at least {@code minMatchRatio} of its significant words
     * (length &gt; 3, not stop words) appear in the normalized summary.
     *
     * @param minMatchRatio minimum ratio of significant words required; must be in [0.0, 1.0]
     * @return loss events for anchors not adequately represented in the summary
     */
    public static List<CompactionLossEvent> validate(String summary, List<Anchor> protectedAnchors,
                                                     double minMatchRatio) {
        var normalizedSummary = normalize(summary);
        var losses = new ArrayList<CompactionLossEvent>();
        for (var anchor : protectedAnchors) {
            var significantWords = extractSignificantWords(anchor.text());
            if (significantWords.isEmpty()) {
                continue;
            }
            long matchCount = significantWords.stream()
                                              .filter(normalizedSummary::contains)
                                              .count();
            double matchRatio = (double) matchCount / significantWords.size();
            if (matchRatio < minMatchRatio) {
                losses.add(new CompactionLossEvent(
                        anchor.id(), anchor.text(), anchor.authority(), anchor.rank()));
            }
        }
        return List.copyOf(losses);
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase().replaceAll("[^a-z0-9 ]", "");
    }

    private static List<String> extractSignificantWords(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        var normalized = normalize(text);
        return Arrays.stream(normalized.split("\\s+"))
                     .filter(w -> w.length() > 3)
                     .filter(w -> !STOP_WORDS.contains(w))
                     .toList();
    }
}
