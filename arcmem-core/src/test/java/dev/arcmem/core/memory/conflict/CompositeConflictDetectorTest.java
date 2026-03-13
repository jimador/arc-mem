package dev.arcmem.core.memory.conflict;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompositeConflictDetector")
class CompositeConflictDetectorTest {

    @Mock private ConflictDetector semanticDetector;

    private final NegationConflictDetector lexicalDetector = new NegationConflictDetector();
    private final SubjectFilter subjectFilter = new SubjectFilter();

    private MemoryUnit unit(String text) {
        return MemoryUnit.withoutTrust("a1", text, 500, Authority.PROVISIONAL, false, 0.9, 0);
    }

    private ConflictDetector.Conflict conflict(String unitText) {
        return new ConflictDetector.Conflict(unit(unitText), "incoming", 0.9, "test");
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

            var result = detector.detect("The king is not dead", List.of(unit("The king is dead")));

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

            var result = detector.detect("The king is not dead", List.of(unit("The king is dead")));

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

            var result = detector.detect("The king is dead", List.of(unit("The king is alive")));

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

            var result = detector.detect("The king died", List.of(unit("The king is alive")));

            assertThat(result).isNotEmpty();
            verify(semanticDetector).detect(anyString(), anyList());
        }
    }

    @Test
    @DisplayName("returns empty for empty memory unit list")
    void emptyUnits() {
        var detector = new CompositeConflictDetector(
                lexicalDetector, semanticDetector, subjectFilter,
                ConflictDetectionStrategy.LEXICAL_THEN_SEMANTIC);

        var result = detector.detect("anything", List.of());

        assertThat(result).isEmpty();
    }

    @Nested
    @DisplayName("INDEXED strategy")
    class IndexedStrategy {

        @Mock private ConflictIndex conflictIndex;

        @Test
        @DisplayName("index hit returns conflict without LLM call")
        void indexHit_noLlmCall() {
            var unit = unit("The sky is blue");
            var entry = new ConflictEntry(unit.id(), unit.text(), unit.authority(),
                    ConflictType.CONTRADICTION, 0.9, Instant.now());
            when(conflictIndex.getConflicts(unit.id())).thenReturn(Set.of(entry));

            var detector = new CompositeConflictDetector(
                    lexicalDetector, semanticDetector, subjectFilter,
                    ConflictDetectionStrategy.INDEXED, conflictIndex);

            var result = detector.detect("The sky is not blue", List.of(unit));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).confidence()).isEqualTo(0.9);
            verify(semanticDetector, never()).detect(anyString(), anyList());
        }

        @Test
        @DisplayName("index miss delegates to semantic detector and caches result")
        void indexMiss_delegatesToSemantic() {
            var unit = unit("The sky is blue");
            when(conflictIndex.getConflicts(unit.id())).thenReturn(Set.of());
            when(semanticDetector.detect(anyString(), anyList()))
                    .thenReturn(List.of(conflict("The sky is blue")));

            var detector = new CompositeConflictDetector(
                    lexicalDetector, semanticDetector, subjectFilter,
                    ConflictDetectionStrategy.INDEXED, conflictIndex);

            var result = detector.detect("The sky is dark", List.of(unit));

            assertThat(result).isNotEmpty();
            verify(semanticDetector).detect(anyString(), anyList());
            verify(conflictIndex).recordConflict(eq(unit.id()), any(ConflictEntry.class));
        }

        @Test
        @DisplayName("null index falls back to LEXICAL_THEN_SEMANTIC")
        void nullIndex_fallsBackToLexicalThenSemantic() {
            var detector = new CompositeConflictDetector(
                    lexicalDetector, semanticDetector, subjectFilter,
                    ConflictDetectionStrategy.INDEXED, null);

            var result = detector.detect("The king is not dead", List.of(unit("The king is dead")));

            assertThat(result).isNotEmpty();
            verify(semanticDetector, never()).detect(anyString(), anyList());
        }
    }

}
