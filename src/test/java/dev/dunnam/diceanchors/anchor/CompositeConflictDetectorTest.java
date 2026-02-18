package dev.dunnam.diceanchors.anchor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompositeConflictDetector")
class CompositeConflictDetectorTest {

    @Mock private ConflictDetector semanticDetector;

    private final NegationConflictDetector lexicalDetector = new NegationConflictDetector();
    private final SubjectFilter subjectFilter = new SubjectFilter();

    private Anchor anchor(String text) {
        return Anchor.withoutTrust("a1", text, 500, Authority.PROVISIONAL, false, 0.9, 0);
    }

    private ConflictDetector.Conflict conflict(String anchorText) {
        return new ConflictDetector.Conflict(anchor(anchorText), "incoming", 0.9, "test");
    }

    @Nested
    @DisplayName("LEXICAL_ONLY strategy")
    class LexicalOnly {

        @Test
        @DisplayName("uses only lexical detector, never calls semantic")
        void lexicalOnlyNoSemantic() {
            var detector = new CompositeConflictDetector(
                    lexicalDetector, semanticDetector, subjectFilter,
                    ConflictDetectionStrategy.LEXICAL_ONLY);

            var result = detector.detect("The king is not dead", List.of(anchor("The king is dead")));

            assertThat(result).isNotEmpty();
            verify(semanticDetector, never()).detect(anyString(), anyList());
        }
    }

    @Nested
    @DisplayName("LEXICAL_THEN_SEMANTIC strategy")
    class LexicalThenSemantic {

        @Test
        @DisplayName("returns lexical conflicts when found, skips semantic")
        void lexicalFoundSkipsSemantic() {
            var detector = new CompositeConflictDetector(
                    lexicalDetector, semanticDetector, subjectFilter,
                    ConflictDetectionStrategy.LEXICAL_THEN_SEMANTIC);

            var result = detector.detect("The king is not dead", List.of(anchor("The king is dead")));

            assertThat(result).isNotEmpty();
            verify(semanticDetector, never()).detect(anyString(), anyList());
        }

        @Test
        @DisplayName("falls back to semantic when lexical finds nothing")
        void fallsBackToSemantic() {
            var detector = new CompositeConflictDetector(
                    lexicalDetector, semanticDetector, subjectFilter,
                    ConflictDetectionStrategy.LEXICAL_THEN_SEMANTIC);

            when(semanticDetector.detect(anyString(), anyList()))
                    .thenReturn(List.of(conflict("The king is alive")));

            var result = detector.detect("The king is dead", List.of(anchor("The king is alive")));

            assertThat(result).isNotEmpty();
            verify(semanticDetector).detect(anyString(), anyList());
        }
    }

    @Nested
    @DisplayName("SEMANTIC_ONLY strategy")
    class SemanticOnly {

        @Test
        @DisplayName("goes directly to semantic detector")
        void directSemantic() {
            var detector = new CompositeConflictDetector(
                    lexicalDetector, semanticDetector, subjectFilter,
                    ConflictDetectionStrategy.SEMANTIC_ONLY);

            when(semanticDetector.detect(anyString(), anyList()))
                    .thenReturn(List.of(conflict("test")));

            var result = detector.detect("The king died", List.of(anchor("The king is alive")));

            assertThat(result).isNotEmpty();
            verify(semanticDetector).detect(anyString(), anyList());
        }
    }

    @Test
    @DisplayName("returns empty for empty anchor list")
    void emptyAnchors() {
        var detector = new CompositeConflictDetector(
                lexicalDetector, semanticDetector, subjectFilter,
                ConflictDetectionStrategy.LEXICAL_THEN_SEMANTIC);

        var result = detector.detect("anything", List.of());

        assertThat(result).isEmpty();
    }
}
