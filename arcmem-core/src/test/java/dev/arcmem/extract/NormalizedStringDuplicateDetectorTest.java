package dev.arcmem.core.extraction;
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NormalizedStringDuplicateDetector")
class NormalizedStringDuplicateDetectorTest {

    private final NormalizedStringDuplicateDetector detector = new NormalizedStringDuplicateDetector();

    private MemoryUnit unit(String text) {
        return MemoryUnit.withoutTrust("a1", text, 500, Authority.PROVISIONAL, false, 0.9, 0);
    }

    @Nested
    @DisplayName("normalization")
    class Normalization {

        @Test
        @DisplayName("lowercases text")
        void lowercases() {
            assertThat(NormalizedStringDuplicateDetector.normalize("The KING Is Dead"))
                    .isEqualTo("the king is dead");
        }

        @Test
        @DisplayName("collapses whitespace")
        void collapsesWhitespace() {
            assertThat(NormalizedStringDuplicateDetector.normalize("the  king   is   dead"))
                    .isEqualTo("the king is dead");
        }

        @Test
        @DisplayName("strips punctuation")
        void stripsPunctuation() {
            assertThat(NormalizedStringDuplicateDetector.normalize("The king is dead!"))
                    .isEqualTo("the king is dead");
        }

        @Test
        @DisplayName("handles combined normalization")
        void combinedNormalization() {
            assertThat(NormalizedStringDuplicateDetector.normalize("  The KING,  is -- DEAD!! "))
                    .isEqualTo("the king is dead");
        }
    }

    @Nested
    @DisplayName("duplicate detection")
    class Detection {

        @Test
        @DisplayName("detects exact match")
        void exactMatch() {
            assertThat(detector.isDuplicate("The king is dead", List.of(unit("The king is dead"))))
                    .isTrue();
        }

        @Test
        @DisplayName("detects case-insensitive match")
        void caseInsensitive() {
            assertThat(detector.isDuplicate("THE KING IS DEAD", List.of(unit("the king is dead"))))
                    .isTrue();
        }

        @Test
        @DisplayName("detects whitespace-variant match")
        void whitespaceVariant() {
            assertThat(detector.isDuplicate("The  king   is dead", List.of(unit("The king is dead"))))
                    .isTrue();
        }

        @Test
        @DisplayName("detects punctuation-variant match")
        void punctuationVariant() {
            assertThat(detector.isDuplicate("The king is dead!", List.of(unit("The king is dead"))))
                    .isTrue();
        }

        @Test
        @DisplayName("returns false for semantically similar but lexically different text")
        void semanticallySimilarButDifferent() {
            assertThat(detector.isDuplicate("The monarch has perished",
                    List.of(unit("The king is dead"))))
                    .isFalse();
        }

        @Test
        @DisplayName("returns false for empty memory unit list")
        void emptyUnitList() {
            assertThat(detector.isDuplicate("Any text", List.of()))
                    .isFalse();
        }

        @Test
        @DisplayName("matches against any memory unit in list")
        void matchesAnyUnit() {
            var units = List.of(
                    unit("The tavern is in Waterdeep"),
                    unit("The king is dead"),
                    unit("Dragons guard the treasure")
            );
            assertThat(detector.isDuplicate("the king is dead", units)).isTrue();
        }
    }
}
