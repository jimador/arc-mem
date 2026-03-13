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

import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.persistence.PropositionNode;
import dev.arcmem.simulator.history.SimulationRunRecord;
import dev.arcmem.simulator.history.SimulationRunRecord.TurnSnapshot;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimToChatBridge")
class SimToChatBridgeTest {

    @Mock
    ConversationService conversationService;

    @Mock
    MemoryUnitRepository contextUnitRepository;

    @Mock
    ArcMemEngine arcMemEngine;

    @InjectMocks
    SimToChatBridge bridge;

    @Nested
    @DisplayName("cloneRunToConversation")
    class CloneRunToConversation {

        @Test
        @DisplayName("createsConversationWithScenarioDerivedTitle")
        void createsConversationWithScenarioDerivedTitle() {
            when(conversationService.createConversation("From sim: dragon-lair"))
                    .thenReturn("conv-1");
            var run = run("dragon-lair", List.of(), List.of());

            bridge.cloneRunToConversation(run);

            verify(conversationService).createConversation("From sim: dragon-lair");
        }

        @Test
        @DisplayName("returnsNewConversationId")
        void returnsNewConversationId() {
            when(conversationService.createConversation(any())).thenReturn("conv-42");
            var run = run("test", List.of(), List.of());

            var result = bridge.cloneRunToConversation(run);

            assertThat(result).isEqualTo("conv-42");
        }

        @Test
        @DisplayName("appendsMessagesInTurnOrderPlayerThenDm")
        void appendsMessagesInTurnOrderPlayerThenDm() {
            when(conversationService.createConversation(any())).thenReturn("conv-1");
            var snapshots = List.of(
                    snapshot(1, "I attack the goblin", "The goblin dodges!"),
                    snapshot(2, "I cast fireball", "The room erupts in flame!")
            );
            var run = run("test", snapshots, List.of());

            bridge.cloneRunToConversation(run);

            var order = inOrder(conversationService);
            order.verify(conversationService).appendMessage("conv-1", "PLAYER", "I attack the goblin");
            order.verify(conversationService).appendMessage("conv-1", "DM", "The goblin dodges!");
            order.verify(conversationService).appendMessage("conv-1", "PLAYER", "I cast fireball");
            order.verify(conversationService).appendMessage("conv-1", "DM", "The room erupts in flame!");
        }

        @Test
        @DisplayName("skipsBlankMessages")
        void skipsBlankMessages() {
            when(conversationService.createConversation(any())).thenReturn("conv-1");
            var snapshots = List.of(snapshot(0, "", "Scene is set"));
            var run = run("test", snapshots, List.of());

            bridge.cloneRunToConversation(run);

            verify(conversationService, never()).appendMessage(any(), eq("PLAYER"), any());
            verify(conversationService).appendMessage("conv-1", "DM", "Scene is set");
        }

        @Test
        @DisplayName("savesAndPromotesEachMemoryUnit")
        void savesAndPromotesEachUnit() {
            when(conversationService.createConversation(any())).thenReturn("conv-1");
            when(contextUnitRepository.saveNode(any())).thenAnswer(inv -> inv.getArgument(0));
            var units = List.of(
                    MemoryUnit.withoutTrust("a1", "The sky is blue", 500, Authority.PROVISIONAL, false, 0.9, 3),
                    MemoryUnit.withoutTrust("a2", "Dragons exist", 700, Authority.RELIABLE, false, 0.95, 7)
            );
            var run = run("test", List.of(), units);

            bridge.cloneRunToConversation(run);

            var nodeCaptor = ArgumentCaptor.forClass(PropositionNode.class);
            verify(contextUnitRepository, org.mockito.Mockito.times(2)).saveNode(nodeCaptor.capture());

            var savedNodes = nodeCaptor.getAllValues();
            assertThat(savedNodes).extracting(PropositionNode::getText)
                    .containsExactly("The sky is blue", "Dragons exist");
            assertThat(savedNodes).allSatisfy(node ->
                    assertThat(node.getContextId()).isEqualTo("conv-1"));

            verify(arcMemEngine).promote(savedNodes.get(0).getId(), 500);
            verify(arcMemEngine).promote(savedNodes.get(1).getId(), 700);
        }

        @Test
        @DisplayName("setsAuthorityAboveProvisional")
        void setsAuthorityAboveProvisional() {
            when(conversationService.createConversation(any())).thenReturn("conv-1");
            when(contextUnitRepository.saveNode(any())).thenAnswer(inv -> inv.getArgument(0));
            var units = List.of(
                    MemoryUnit.withoutTrust("a1", "Provisional fact", 400, Authority.PROVISIONAL, false, 0.8, 1),
                    MemoryUnit.withoutTrust("a2", "Reliable fact", 600, Authority.RELIABLE, false, 0.9, 7)
            );
            var run = run("test", List.of(), units);

            bridge.cloneRunToConversation(run);

            var nodeCaptor = ArgumentCaptor.forClass(PropositionNode.class);
            verify(contextUnitRepository, org.mockito.Mockito.times(2)).saveNode(nodeCaptor.capture());
            var nodes = nodeCaptor.getAllValues();

            verify(contextUnitRepository, never()).setAuthority(eq(nodes.get(0).getId()), any());
            verify(contextUnitRepository).setAuthority(nodes.get(1).getId(), "RELIABLE");
        }

        @Test
        @DisplayName("copiesReinforcementCountImportanceAndDecayToNode")
        void copiesReinforcementCountImportanceAndDecayToNode() {
            when(conversationService.createConversation(any())).thenReturn("conv-1");
            when(contextUnitRepository.saveNode(any())).thenAnswer(inv -> inv.getArgument(0));
            var unit = new MemoryUnit("a1", "Rich unit", 600, Authority.RELIABLE, false,
                    0.9, 5, null, 0.85, 0.3, MemoryTier.HOT, null);
            var run = run("test", List.of(), List.of(unit));

            bridge.cloneRunToConversation(run);

            var nodeCaptor = ArgumentCaptor.forClass(PropositionNode.class);
            verify(contextUnitRepository).saveNode(nodeCaptor.capture());
            var node = nodeCaptor.getValue();
            assertThat(node.getReinforcementCount()).isEqualTo(5);
            assertThat(node.getImportance()).isEqualTo(0.85);
            assertThat(node.getDecay()).isEqualTo(0.3);
        }

        @Test
        @DisplayName("updatesPinnedForPinnedMemoryUnits")
        void updatesPinnedForPinnedUnits() {
            when(conversationService.createConversation(any())).thenReturn("conv-1");
            when(contextUnitRepository.saveNode(any())).thenAnswer(inv -> inv.getArgument(0));
            var units = List.of(
                    MemoryUnit.withoutTrust("a1", "Unpinned", 300, Authority.PROVISIONAL, false, 0.7, 0),
                    MemoryUnit.withoutTrust("a2", "Pinned", 800, Authority.RELIABLE, true, 0.95, 10)
            );
            var run = run("test", List.of(), units);

            bridge.cloneRunToConversation(run);

            var nodeCaptor = ArgumentCaptor.forClass(PropositionNode.class);
            verify(contextUnitRepository, org.mockito.Mockito.times(2)).saveNode(nodeCaptor.capture());
            var nodes = nodeCaptor.getAllValues();

            verify(contextUnitRepository, never()).updatePinned(eq(nodes.get(0).getId()), eq(true));
            verify(contextUnitRepository).updatePinned(nodes.get(1).getId(), true);
        }
    }

    private static SimulationRunRecord run(String scenarioId,
                                           List<TurnSnapshot> snapshots,
                                           List<MemoryUnit> finalUnits) {
        return new SimulationRunRecord(
                "run-1", scenarioId, Instant.now(), Instant.now(),
                snapshots, 0, finalUnits, true, 4000,
                null, null, null
        );
    }

    private static TurnSnapshot snapshot(int turn, String player, String dm) {
        return new TurnSnapshot(turn, null, List.of(), player, dm,
                List.of(), null, List.of(), true, null);
    }
}
