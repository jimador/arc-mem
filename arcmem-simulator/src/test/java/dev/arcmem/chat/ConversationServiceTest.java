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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationService")
class ConversationServiceTest {

    @Mock
    ConversationRepository repository;

    @Mock
    MemoryUnitRepository contextUnitRepository;

    @InjectMocks
    ConversationService service;

    @Nested
    @DisplayName("createConversation")
    class CreateConversation {

        @Test
        @DisplayName("returnsNonNullUuid")
        void returnsNonNullUuid() {
            var id = service.createConversation();

            assertThat(id).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("delegatesToRepositoryWithUntitledTitle")
        void delegatesToRepositoryWithUntitledTitle() {
            var id = service.createConversation();

            verify(repository).createConversation(id, "Untitled");
        }
    }

    @Nested
    @DisplayName("appendMessage")
    class AppendMessage {

        @Test
        @DisplayName("usesCountMessagesAsOrdinal")
        void usesCountMessagesAsOrdinal() {
            when(repository.countMessages("conv-1")).thenReturn(3);

            service.appendMessage("conv-1", "user", "hello");

            verify(repository).appendMessage("conv-1", "user", "hello", 3);
        }

        @Test
        @DisplayName("ordinalsIncrementAcrossMultipleCalls")
        void ordinalsIncrementAcrossMultipleCalls() {
            when(repository.countMessages("conv-1")).thenReturn(0, 1, 2);

            service.appendMessage("conv-1", "user", "first");
            service.appendMessage("conv-1", "assistant", "second");
            service.appendMessage("conv-1", "user", "third");

            verify(repository).appendMessage(eq("conv-1"), eq("user"), eq("first"), eq(0));
            verify(repository).appendMessage(eq("conv-1"), eq("assistant"), eq("second"), eq(1));
            verify(repository).appendMessage(eq("conv-1"), eq("user"), eq("third"), eq(2));
        }
    }

    @Nested
    @DisplayName("loadConversation")
    class LoadConversation {

        @Test
        @DisplayName("returnsOrderedMessagesFromRepository")
        void returnsOrderedMessagesFromRepository() {
            var messages = List.of(
                new ChatMessageRecord("conv-1", "user", "hi", 0, Instant.now()),
                new ChatMessageRecord("conv-1", "assistant", "hello", 1, Instant.now())
            );
            when(repository.loadMessages("conv-1")).thenReturn(messages);

            var result = service.loadConversation("conv-1");

            assertThat(result).isEqualTo(messages);
        }

        @Test
        @DisplayName("nonexistentIdReturnsEmptyList")
        void nonexistentIdReturnsEmptyList() {
            when(repository.loadMessages("missing")).thenReturn(List.of());

            var result = service.loadConversation("missing");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findConversation")
    class FindConversation {

        @Test
        @DisplayName("delegatesToRepository")
        void delegatesToRepository() {
            var record = new ConversationRecord("conv-1", "Untitled", Instant.now(), 0);
            when(repository.findConversation("conv-1")).thenReturn(Optional.of(record));

            var result = service.findConversation("conv-1");

            assertThat(result).contains(record);
        }

        @Test
        @DisplayName("missingConversationReturnsEmpty")
        void missingConversationReturnsEmpty() {
            when(repository.findConversation("nope")).thenReturn(Optional.empty());

            var result = service.findConversation("nope");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("listConversations")
    class ListConversations {

        @Test
        @DisplayName("delegatesToRepository")
        void delegatesToRepository() {
            var conversations = List.of(
                new ConversationRecord("conv-1", "Untitled", Instant.now(), 2),
                new ConversationRecord("conv-2", "Untitled", Instant.now(), 5)
            );
            when(repository.listConversations()).thenReturn(conversations);

            var result = service.listConversations();

            assertThat(result).isEqualTo(conversations);
        }
    }

    @Nested
    @DisplayName("cloneConversation")
    class CloneConversation {

        @Test
        @DisplayName("clonesWithDerivedTitle")
        void clonesWithDerivedTitle() {
            var source = new ConversationRecord("src-1", "Dragon Lair", Instant.now(), 2);
            when(repository.findConversation("src-1")).thenReturn(Optional.of(source));
            when(repository.loadMessages("src-1")).thenReturn(List.of());
            when(contextUnitRepository.findActiveUnits("src-1")).thenReturn(List.of());

            service.cloneConversation("src-1");

            verify(repository).createConversation(any(), eq("Clone of: Dragon Lair"));
        }

        @Test
        @DisplayName("clonesAllMessagesInOrder")
        void clonesAllMessagesInOrder() {
            var source = new ConversationRecord("src-1", "Test", Instant.now(), 3);
            when(repository.findConversation("src-1")).thenReturn(Optional.of(source));
            var messages = List.of(
                    new ChatMessageRecord("src-1", "PLAYER", "I enter the cave", 0, Instant.now()),
                    new ChatMessageRecord("src-1", "DM", "You see a dragon", 1, Instant.now()),
                    new ChatMessageRecord("src-1", "PLAYER", "I attack!", 2, Instant.now())
            );
            when(repository.loadMessages("src-1")).thenReturn(messages);
            when(contextUnitRepository.findActiveUnits("src-1")).thenReturn(List.of());
            when(repository.countMessages(any())).thenReturn(0, 1, 2);

            service.cloneConversation("src-1");

            var order = inOrder(repository);
            order.verify(repository).appendMessage(any(), eq("PLAYER"), eq("I enter the cave"), eq(0));
            order.verify(repository).appendMessage(any(), eq("DM"), eq("You see a dragon"), eq(1));
            order.verify(repository).appendMessage(any(), eq("PLAYER"), eq("I attack!"), eq(2));
        }

        @Test
        @DisplayName("clonesActiveMemoryUnitsWithFullState")
        void clonesActiveUnitsWithFullState() {
            var source = new ConversationRecord("src-1", "Test", Instant.now(), 0);
            when(repository.findConversation("src-1")).thenReturn(Optional.of(source));
            when(repository.loadMessages("src-1")).thenReturn(List.of());

            var unit = new PropositionNode(UUID.randomUUID().toString(), "test-context", "The dragon is red", 0.95, 0.0, null, List.of(),
                    Instant.now(), Instant.now(), PropositionStatus.ACTIVE, null, List.of());
            unit.setContextId("src-1");
            unit.setRank(700);
            unit.setAuthority("RELIABLE");
            unit.setPinned(true);
            unit.setReinforcementCount(5);
            unit.setImportance(0.8);
            unit.setDecay(0.2);
            unit.setMemoryTier("HOT");
            unit.setDecayType("GRADUAL");
            unit.setAuthorityCeiling("CANON");
            when(contextUnitRepository.findActiveUnits("src-1")).thenReturn(List.of(unit));
            when(contextUnitRepository.saveNode(any())).thenAnswer(inv -> inv.getArgument(0));

            service.cloneConversation("src-1");

            var nodeCaptor = ArgumentCaptor.forClass(PropositionNode.class);
            verify(contextUnitRepository).saveNode(nodeCaptor.capture());
            var cloned = nodeCaptor.getValue();

            assertThat(cloned.getId()).isNotEqualTo(unit.getId());
            assertThat(cloned.getContextId()).isNotEqualTo("src-1");
            assertThat(cloned.getText()).isEqualTo("The dragon is red");
            assertThat(cloned.getConfidence()).isEqualTo(0.95);
            assertThat(cloned.getRank()).isEqualTo(700);
            assertThat(cloned.getAuthority()).isEqualTo("RELIABLE");
            assertThat(cloned.isPinned()).isTrue();
            assertThat(cloned.getReinforcementCount()).isEqualTo(5);
            assertThat(cloned.getImportance()).isEqualTo(0.8);
            assertThat(cloned.getDecay()).isEqualTo(0.2);
            assertThat(cloned.getMemoryTier()).isEqualTo("HOT");
            assertThat(cloned.getDecayType()).isEqualTo("GRADUAL");
            assertThat(cloned.getAuthorityCeiling()).isEqualTo("CANON");
            assertThat(cloned.getSupersededBy()).isNull();
            assertThat(cloned.getSupersedes()).isNull();
        }

        @Test
        @DisplayName("missingSourceThrowsException")
        void missingSourceThrowsException() {
            when(repository.findConversation("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cloneConversation("missing"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("missing");
        }
    }
}
