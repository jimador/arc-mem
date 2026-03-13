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
import static org.mockito.Mockito.when;

@DisplayName("MemoryUnitQueryTools")
@ExtendWith(MockitoExtension.class)
class MemoryUnitQueryToolsTest {

    private static final String CONTEXT_ID = "chat";

    @Mock
    private ArcMemEngine engine;

    @Mock
    private MemoryUnitRepository repository;

    @Nested
    @DisplayName("queryFacts")
    class QueryFacts {

        @Test
        @DisplayName("returns matching memory units from semantic search")
        void returnsMatchingUnits() {
            var node = unitNode("a1", "The king is dead", 500, "RELIABLE", false);
            var scored = new MemoryUnitRepository.ScoredProposition("a1", "The king is dead", 0.9, "ACTIVE", 0.85);
            when(repository.semanticSearch("king", CONTEXT_ID, 10, 0.5)).thenReturn(List.of(scored));
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));

            var tools = new MemoryUnitQueryTools(engine, repository, null, CONTEXT_ID);
            var results = tools.queryFacts("king");

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().id()).isEqualTo("a1");
            assertThat(results.getFirst().text()).isEqualTo("The king is dead");
            assertThat(results.getFirst().rank()).isEqualTo(500);
            assertThat(results.getFirst().authority()).isEqualTo("RELIABLE");
        }

        @Test
        @DisplayName("returns empty list for no matches")
        void returnsEmptyForNoMatches() {
            when(repository.semanticSearch("dragons", CONTEXT_ID, 10, 0.5)).thenReturn(List.of());

            var tools = new MemoryUnitQueryTools(engine, repository, null, CONTEXT_ID);
            var results = tools.queryFacts("dragons");

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("filters out non-context-unit propositions")
        void filtersNonUnits() {
            var node = plainPropositionNode("p1", "Some fact");
            var scored = new MemoryUnitRepository.ScoredProposition("p1", "Some fact", 0.9, "ACTIVE", 0.75);
            when(repository.semanticSearch("fact", CONTEXT_ID, 10, 0.5)).thenReturn(List.of(scored));
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.of(node));

            var tools = new MemoryUnitQueryTools(engine, repository, null, CONTEXT_ID);
            var results = tools.queryFacts("fact");

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("listMemoryUnits")
    class ListUnits {

        @Test
        @DisplayName("returns all active memory units in activation score order")
        void returnsAllActiveUnits() {
            var units = List.of(
                    MemoryUnit.withoutTrust("a1", "Fact one", 700, Authority.RELIABLE, false, 0.9, 3),
                    MemoryUnit.withoutTrust("a2", "Fact two", 500, Authority.PROVISIONAL, true, 0.8, 1)
            );
            when(engine.inject(CONTEXT_ID)).thenReturn(units);

            var tools = new MemoryUnitQueryTools(engine, repository, null, CONTEXT_ID);
            var results = tools.listUnits();

            assertThat(results).hasSize(2);
            assertThat(results.get(0).id()).isEqualTo("a1");
            assertThat(results.get(0).rank()).isEqualTo(700);
            assertThat(results.get(1).id()).isEqualTo("a2");
            assertThat(results.get(1).pinned()).isTrue();
        }

        @Test
        @DisplayName("returns empty list when no memory units exist")
        void returnsEmptyWhenNoUnits() {
            when(engine.inject(CONTEXT_ID)).thenReturn(List.of());

            var tools = new MemoryUnitQueryTools(engine, repository, null, CONTEXT_ID);
            var results = tools.listUnits();

            assertThat(results).isEmpty();
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
