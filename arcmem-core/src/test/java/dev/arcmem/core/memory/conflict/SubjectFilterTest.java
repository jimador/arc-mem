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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubjectFilter")
class SubjectFilterTest {

    private final SubjectFilter filter = new SubjectFilter();

    private MemoryUnit unit(String id, String text) {
        return MemoryUnit.withoutTrust(id, text, 500, Authority.PROVISIONAL, false, 0.9, 0);
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
        @DisplayName("returns memory units with shared subjects")
        void sharedSubjects() {
            var units = List.of(
                    unit("a1", "The king is alive"),
                    unit("a2", "The dragon guards treasure"),
                    unit("a3", "The tavern is warm")
            );
            var result = filter.filterCandidates("The king is dead", units);
            assertThat(result).extracting(MemoryUnit::id).contains("a1");
        }

        @Test
        @DisplayName("returns all memory units when no subjects found in incoming")
        void noSubjectsFallback() {
            var units = List.of(unit("a1", "Something"), unit("a2", "Else"));
            var result = filter.filterCandidates("it is so", units);
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("returns all memory units when no overlap found")
        void noOverlapFallback() {
            var units = List.of(unit("a1", "The dragon flies"));
            var result = filter.filterCandidates("Mars is round", units);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("handles empty memory unit list")
        void emptyUnits() {
            var result = filter.filterCandidates("The king", List.of());
            assertThat(result).isEmpty();
        }
    }
}
