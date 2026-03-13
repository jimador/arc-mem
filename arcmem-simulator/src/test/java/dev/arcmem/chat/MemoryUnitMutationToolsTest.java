package dev.arcmem.simulator.chat;
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

import dev.arcmem.core.spi.llm.*;
import dev.arcmem.simulator.engine.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

import com.embabel.dice.proposition.PropositionStatus;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.persistence.PropositionNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MemoryUnitMutationTools")
@ExtendWith(MockitoExtension.class)
class MemoryUnitMutationToolsTest {

    private static final String CONTEXT_ID = "chat";

    @Mock
    private ArcMemEngine engine;

    @Mock
    private MemoryUnitRepository repository;

    @Nested
    @DisplayName("pinFact")
    class PinFact {

        @Test
        @DisplayName("succeeds on active memory unit")
        void succeedsOnActiveUnit() {
            var node = unitNode("a1", "Important fact", 500, "PROVISIONAL", false);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));

            var tools = new MemoryUnitMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.pinFact("a1");

            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("Fact pinned successfully");
            verify(repository).updatePinned("a1", true);
        }

        @Test
        @DisplayName("fails on non-existent memory unit")
        void failsOnNonExistentUnit() {
            when(repository.findPropositionNodeById("missing")).thenReturn(Optional.empty());

            var tools = new MemoryUnitMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.pinFact("missing");

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("not found");
        }

        @Test
        @DisplayName("fails on archived memory unit")
        void failsOnArchivedUnit() {
            var node = unitNode("a1", "Old fact", 500, "PROVISIONAL", false);
            node.setStatus(PropositionStatus.SUPERSEDED);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));

            var tools = new MemoryUnitMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.pinFact("a1");

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("archived");
        }

        @Test
        @DisplayName("fails on non-context-unit proposition")
        void failsOnNonUnit() {
            var node = plainPropositionNode("p1", "Not an unit");
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.of(node));

            var tools = new MemoryUnitMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.pinFact("p1");

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("Not an unit");
        }
    }

    @Nested
    @DisplayName("unpinFact")
    class UnpinFact {

        @Test
        @DisplayName("succeeds on pinned memory unit")
        void succeedsOnPinnedUnit() {
            var node = unitNode("a1", "Pinned fact", 600, "RELIABLE", true);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));

            var tools = new MemoryUnitMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.unpinFact("a1");

            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("Fact unpinned");
            verify(repository).updatePinned("a1", false);
        }

        @Test
        @DisplayName("fails on non-existent memory unit")
        void failsOnNonExistentUnit() {
            when(repository.findPropositionNodeById("missing")).thenReturn(Optional.empty());

            var tools = new MemoryUnitMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.unpinFact("missing");

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("not found");
        }

        @Test
        @DisplayName("fails on unpinned memory unit")
        void failsOnUnpinnedUnit() {
            var node = unitNode("a1", "Not pinned", 500, "PROVISIONAL", false);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));

            var tools = new MemoryUnitMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.unpinFact("a1");

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("not pinned");
        }

        @Test
        @DisplayName("fails on CANON memory unit")
        void failsOnCanonUnit() {
            var node = unitNode("a1", "Canon fact", 800, "CANON", true);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));

            var tools = new MemoryUnitMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.unpinFact("a1");

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("CANON");
        }
    }

    @Nested
    @DisplayName("demoteMemoryUnit")
    class DemoteUnit {

        @Test
        @DisplayName("demotes RELIABLE memory unit and returns non-empty result string")
        void demotesReliableUnit() {
            var node = unitNode("a1", "Reliable fact", 700, "RELIABLE", false);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));

            var tools = new MemoryUnitMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.demoteUnit("a1");

            verify(engine).demote("a1", DemotionReason.MANUAL);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("non-existent memory unit returns message containing 'not found'")
        void nonExistentUnitReturnsNotFound() {
            when(repository.findPropositionNodeById("missing")).thenReturn(Optional.empty());

            var tools = new MemoryUnitMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.demoteUnit("missing");

            assertThat(result).containsIgnoringCase("not found");
            verify(engine, never()).demote(anyString(), any());
        }

        @Test
        @DisplayName("CANON memory unit triggers demote through engine (gate handled by engine)")
        void canonUnitDemotedViaEngine() {
            var node = unitNode("a2", "Canon fact", 850, "CANON", true);
            when(repository.findPropositionNodeById("a2")).thenReturn(Optional.of(node));

            var tools = new MemoryUnitMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.demoteUnit("a2");

            verify(engine).demote("a2", DemotionReason.MANUAL);
            assertThat(result).isNotEmpty();
        }
    }

    private static PropositionNode unitNode(String id, String text, int rank, String authority, boolean pinned) {
        return new PropositionNode(
                id, CONTEXT_ID, text, 0.9, 0.0, null, List.of(),
                java.time.Instant.now(), java.time.Instant.now(), PropositionStatus.ACTIVE,
                null, List.of(), rank, authority, pinned, null, null, 0, 0.0, null,
                null, null, null, null, null, null
        );
    }

    private static PropositionNode plainPropositionNode(String id, String text) {
        return new PropositionNode(
                id, CONTEXT_ID, text, 0.8, 0.0, null, List.of(),
                java.time.Instant.now(), java.time.Instant.now(), PropositionStatus.ACTIVE,
                null, List.of()
        );
    }
}
