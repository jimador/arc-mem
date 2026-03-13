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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@DisplayName("PrologAuditPreFilter")
@ExtendWith(MockitoExtension.class)
class PrologAuditPreFilterTest {

    private final MemoryUnitPrologProjector realProjector = new MemoryUnitPrologProjector();

    @Mock
    private MemoryUnitPrologProjector mockProjector;

    private MemoryUnit unit(String id, String text) {
        return MemoryUnit.withoutTrust(id, text, 500, Authority.RELIABLE, false, 0.9, 0);
    }

    @Nested
    @DisplayName("flagContradictingMemoryUnits")
    class FlagContradictingUnits {

        @Test
        @DisplayName("flags both memory units in a contradicting pair")
        void flagsContradictingPair() {
            var filter = new PrologAuditPreFilter(realProjector);
            var units = List.of(
                    unit("anc-001", "The guardian is alive"),
                    unit("anc-002", "The guardian is dead")
            );
            var flagged = filter.flagContradictingUnits(units);
            assertThat(flagged).contains("anc-001", "anc-002");
        }

        @Test
        @DisplayName("returns empty set when no contradictions")
        void returnsEmptySetForNonContradicting() {
            var filter = new PrologAuditPreFilter(realProjector);
            var units = List.of(
                    unit("anc-001", "The tavern serves ale"),
                    unit("anc-002", "The king sits on his throne")
            );
            var flagged = filter.flagContradictingUnits(units);
            assertThat(flagged).isEmpty();
        }

        @Test
        @DisplayName("returns empty set on projector failure")
        void returnsEmptySetOnFailure() {
            when(mockProjector.project(anyList()))
                    .thenThrow(new RuntimeException("Prolog failure"));
            var filter = new PrologAuditPreFilter(mockProjector);
            var units = List.of(unit("anc-001", "some unit"));
            var flagged = filter.flagContradictingUnits(units);
            assertThat(flagged).isEmpty();
        }

        @Test
        @DisplayName("returns empty set for empty memory unit list")
        void returnsEmptySetForEmptyUnits() {
            var filter = new PrologAuditPreFilter(realProjector);
            var flagged = filter.flagContradictingUnits(List.of());
            assertThat(flagged).isEmpty();
        }
    }
}
