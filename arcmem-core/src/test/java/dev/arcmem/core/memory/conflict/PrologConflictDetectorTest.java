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

import com.embabel.dice.projection.prolog.PrologEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("PrologConflictDetector")
@ExtendWith(MockitoExtension.class)
class PrologConflictDetectorTest {

    private final MemoryUnitPrologProjector realProjector = new MemoryUnitPrologProjector();

    @Mock
    private MemoryUnitPrologProjector mockProjector;

    private MemoryUnit unit(String id, String text) {
        return MemoryUnit.withoutTrust(id, text, 500, Authority.RELIABLE, false, 0.9, 0);
    }

    @Nested
    @DisplayName("detect")
    class Detect {

        @Test
        @DisplayName("detects negation contradiction between incoming and existing memory unit")
        void detectsNegationContradiction() {
            var detector = new PrologConflictDetector(realProjector);
            var existing = List.of(unit("anc-001", "The guardian is alive"));
            var conflicts = detector.detect("The guardian is dead", existing);
            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.getFirst().existing().id()).isEqualTo("anc-001");
            assertThat(conflicts.getFirst().confidence()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("returns empty for non-contradicting memory units")
        void returnsEmptyForNonContradicting() {
            var detector = new PrologConflictDetector(realProjector);
            var existing = List.of(unit("anc-001", "The tavern serves ale"));
            var conflicts = detector.detect("The king is on his throne", existing);
            assertThat(conflicts).isEmpty();
        }

        @Test
        @DisplayName("returns empty list on projector failure")
        void returnsEmptyOnProjectorFailure() {
            when(mockProjector.projectWithIncoming(anyList(), anyString()))
                    .thenThrow(new RuntimeException("Prolog engine failure"));
            var detector = new PrologConflictDetector(mockProjector);
            var existing = List.of(unit("anc-001", "some unit"));
            var conflicts = detector.detect("some incoming", existing);
            assertThat(conflicts).isEmpty();
        }

        @Test
        @DisplayName("returns empty for empty memory unit list")
        void returnsEmptyForEmptyUnits() {
            var detector = new PrologConflictDetector(realProjector);
            var conflicts = detector.detect("anything", List.of());
            assertThat(conflicts).isEmpty();
        }
    }
}
