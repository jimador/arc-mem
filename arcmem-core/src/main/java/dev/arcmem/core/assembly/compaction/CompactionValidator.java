package dev.arcmem.core.assembly.compaction;
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


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Validates compaction summaries to ensure protected units survive summarization.
 * <p>
 * Pure utility class with no Spring dependencies. Checks whether the majority
 * (&gt;50%) of significant words from each protected unit appear in the summary text.
 */
public final class CompactionValidator {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "is", "are", "was", "were",
            "in", "on", "at", "to", "of", "and", "or",
            "for", "with", "that", "this", "has", "had", "have"
    );

    private CompactionValidator() {}

    /**
     * Validates that protected units are represented in the compaction summary.
     * An unit is considered "found" if at least {@code minMatchRatio} of its significant words
     * (length &gt; 3, not stop words) appear in the normalized summary.
     *
     * @param minMatchRatio minimum ratio of significant words required; must be in [0.0, 1.0]
     * @return loss events for units not adequately represented in the summary
     */
    public static List<CompactionLossEvent> validate(String summary, List<MemoryUnit> protectedUnits,
                                                     double minMatchRatio) {
        var normalizedSummary = normalize(summary);
        var losses = new ArrayList<CompactionLossEvent>();
        for (var unit : protectedUnits) {
            var significantWords = extractSignificantWords(unit.text());
            if (significantWords.isEmpty()) {
                continue;
            }
            long matchCount = significantWords.stream()
                                              .filter(normalizedSummary::contains)
                                              .count();
            double matchRatio = (double) matchCount / significantWords.size();
            if (matchRatio < minMatchRatio) {
                losses.add(new CompactionLossEvent(
                        unit.id(), unit.text(), unit.authority(), unit.rank()));
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
