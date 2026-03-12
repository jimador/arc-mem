package dev.dunnam.diceanchors.anchor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubjectFilter")
class SubjectFilterTest {

    private final SubjectFilter filter = new SubjectFilter();

    private Anchor anchor(String id, String text) {
        return Anchor.withoutTrust(id, text, 500, Authority.PROVISIONAL, false, 0.9, 0);
    }

    @Nested
    @DisplayName("subject extraction")
    class SubjectExtraction {

        @Test
        @DisplayName("extracts capitalized words as named entities")
        void capitalizedWords() {
            var subjects = filter.extractSubjects("Mars is a red planet");
            assertThat(subjects).contains("mars");
        }

        @Test
        @DisplayName("extracts topic markers")
        void topicMarkers() {
            var subjects = filter.extractSubjects("This is about dragons");
            assertThat(subjects).contains("dragons");
        }

        @Test
        @DisplayName("extracts nouns after determiners")
        void determinerNouns() {
            var subjects = filter.extractSubjects("The king is dead");
            assertThat(subjects).contains("king");
        }

        @Test
        @DisplayName("returns empty for text with no subjects")
        void noSubjects() {
            var subjects = filter.extractSubjects("it is so");
            assertThat(subjects).isEmpty();
        }
    }

    @Nested
    @DisplayName("candidate filtering")
    class Filtering {

        @Test
        @DisplayName("returns anchors with shared subjects")
        void sharedSubjects() {
            var anchors = List.of(
                    anchor("a1", "The king is alive"),
                    anchor("a2", "The dragon guards treasure"),
                    anchor("a3", "The tavern is warm")
            );
            var result = filter.filterCandidates("The king is dead", anchors);
            assertThat(result).extracting(Anchor::id).contains("a1");
        }

        @Test
        @DisplayName("returns all anchors when no subjects found in incoming")
        void noSubjectsFallback() {
            var anchors = List.of(anchor("a1", "Something"), anchor("a2", "Else"));
            var result = filter.filterCandidates("it is so", anchors);
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("returns all anchors when no overlap found")
        void noOverlapFallback() {
            var anchors = List.of(anchor("a1", "The dragon flies"));
            var result = filter.filterCandidates("Mars is round", anchors);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("handles empty anchor list")
        void emptyAnchors() {
            var result = filter.filterCandidates("The king", List.of());
            assertThat(result).isEmpty();
        }
    }
}
