package dev.dunnam.diceanchors.chat;

import com.embabel.dice.proposition.PropositionStatus;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.DemotionReason;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.persistence.PropositionNode;
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

@DisplayName("AnchorMutationTools")
@ExtendWith(MockitoExtension.class)
class AnchorMutationToolsTest {

    private static final String CONTEXT_ID = "chat";

    @Mock
    private AnchorEngine engine;

    @Mock
    private AnchorRepository repository;

    @Nested
    @DisplayName("pinFact")
    class PinFact {

        @Test
        @DisplayName("succeeds on active anchor")
        void succeedsOnActiveAnchor() {
            var node = anchorNode("a1", "Important fact", 500, "PROVISIONAL", false);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));

            var tools = new AnchorMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.pinFact("a1");

            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("Fact pinned successfully");
            verify(repository).updatePinned("a1", true);
        }

        @Test
        @DisplayName("fails on non-existent anchor")
        void failsOnNonExistentAnchor() {
            when(repository.findPropositionNodeById("missing")).thenReturn(Optional.empty());

            var tools = new AnchorMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.pinFact("missing");

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("not found");
        }

        @Test
        @DisplayName("fails on archived anchor")
        void failsOnArchivedAnchor() {
            var node = anchorNode("a1", "Old fact", 500, "PROVISIONAL", false);
            node.setStatus(PropositionStatus.SUPERSEDED);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));

            var tools = new AnchorMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.pinFact("a1");

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("archived");
        }

        @Test
        @DisplayName("fails on non-anchor proposition")
        void failsOnNonAnchor() {
            var node = plainPropositionNode("p1", "Not an anchor");
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.of(node));

            var tools = new AnchorMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.pinFact("p1");

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("Not an anchor");
        }
    }

    @Nested
    @DisplayName("unpinFact")
    class UnpinFact {

        @Test
        @DisplayName("succeeds on pinned anchor")
        void succeedsOnPinnedAnchor() {
            var node = anchorNode("a1", "Pinned fact", 600, "RELIABLE", true);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));

            var tools = new AnchorMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.unpinFact("a1");

            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("Fact unpinned");
            verify(repository).updatePinned("a1", false);
        }

        @Test
        @DisplayName("fails on non-existent anchor")
        void failsOnNonExistentAnchor() {
            when(repository.findPropositionNodeById("missing")).thenReturn(Optional.empty());

            var tools = new AnchorMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.unpinFact("missing");

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("not found");
        }

        @Test
        @DisplayName("fails on unpinned anchor")
        void failsOnUnpinnedAnchor() {
            var node = anchorNode("a1", "Not pinned", 500, "PROVISIONAL", false);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));

            var tools = new AnchorMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.unpinFact("a1");

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("not pinned");
        }

        @Test
        @DisplayName("fails on CANON anchor")
        void failsOnCanonAnchor() {
            var node = anchorNode("a1", "Canon fact", 800, "CANON", true);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));

            var tools = new AnchorMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.unpinFact("a1");

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("CANON");
        }
    }

    @Nested
    @DisplayName("demoteAnchor")
    class DemoteAnchor {

        @Test
        @DisplayName("demotes RELIABLE anchor and returns non-empty result string")
        void demotesReliableAnchor() {
            var node = anchorNode("a1", "Reliable fact", 700, "RELIABLE", false);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));

            var tools = new AnchorMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.demoteAnchor("a1");

            verify(engine).demote("a1", DemotionReason.MANUAL);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("non-existent anchor returns message containing 'not found'")
        void nonExistentAnchorReturnsNotFound() {
            when(repository.findPropositionNodeById("missing")).thenReturn(Optional.empty());

            var tools = new AnchorMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.demoteAnchor("missing");

            assertThat(result).containsIgnoringCase("not found");
            verify(engine, never()).demote(anyString(), any());
        }

        @Test
        @DisplayName("CANON anchor triggers demote through engine (gate handled by engine)")
        void canonAnchorDemotedViaEngine() {
            var node = anchorNode("a2", "Canon fact", 850, "CANON", true);
            when(repository.findPropositionNodeById("a2")).thenReturn(Optional.of(node));

            var tools = new AnchorMutationTools(engine, repository, CONTEXT_ID);
            var result = tools.demoteAnchor("a2");

            verify(engine).demote("a2", DemotionReason.MANUAL);
            assertThat(result).isNotEmpty();
        }
    }

    private static PropositionNode anchorNode(String id, String text, int rank, String authority, boolean pinned) {
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
