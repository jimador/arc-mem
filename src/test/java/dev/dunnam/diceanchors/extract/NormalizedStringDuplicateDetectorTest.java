package dev.dunnam.diceanchors.extract;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.Authority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NormalizedStringDuplicateDetector")
class NormalizedStringDuplicateDetectorTest {

    private final NormalizedStringDuplicateDetector detector = new NormalizedStringDuplicateDetector();

    private Anchor anchor(String text) {
        return Anchor.withoutTrust("a1", text, 500, Authority.PROVISIONAL, false, 0.9, 0);
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
            assertThat(detector.isDuplicate("The king is dead", List.of(anchor("The king is dead"))))
                    .isTrue();
        }

        @Test
        @DisplayName("detects case-insensitive match")
        void caseInsensitive() {
            assertThat(detector.isDuplicate("THE KING IS DEAD", List.of(anchor("the king is dead"))))
                    .isTrue();
        }

        @Test
        @DisplayName("detects whitespace-variant match")
        void whitespaceVariant() {
            assertThat(detector.isDuplicate("The  king   is dead", List.of(anchor("The king is dead"))))
                    .isTrue();
        }

        @Test
        @DisplayName("detects punctuation-variant match")
        void punctuationVariant() {
            assertThat(detector.isDuplicate("The king is dead!", List.of(anchor("The king is dead"))))
                    .isTrue();
        }

        @Test
        @DisplayName("returns false for semantically similar but lexically different text")
        void semanticallySimilarButDifferent() {
            assertThat(detector.isDuplicate("The monarch has perished",
                    List.of(anchor("The king is dead"))))
                    .isFalse();
        }

        @Test
        @DisplayName("returns false for empty anchor list")
        void emptyAnchorList() {
            assertThat(detector.isDuplicate("Any text", List.of()))
                    .isFalse();
        }

        @Test
        @DisplayName("matches against any anchor in list")
        void matchesAnyAnchor() {
            var anchors = List.of(
                    anchor("The tavern is in Waterdeep"),
                    anchor("The king is dead"),
                    anchor("Dragons guard the treasure")
            );
            assertThat(detector.isDuplicate("the king is dead", anchors)).isTrue();
        }
    }
}
