package dev.dunnam.diceanchors.chat;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.persistence.PropositionNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatContextInitializer")
class ChatContextInitializerTest {

    private static final String CONTEXT_ID = "chat-ctx-1";

    @Mock private AnchorEngine anchorEngine;
    @Mock private AnchorRepository repository;

    @Nested
    @DisplayName("seeding on first init")
    class SeedingOnFirstInit {

        @Test
        @DisplayName("creates propositions and promotes them on first init")
        void seedsCreatedOnFirstInit() {
            var initializer = initializerWith(enabledSeedConfig(
                    List.of(new DiceAnchorsProperties.ChatSeedAnchor(
                            "The sky is blue", "RELIABLE", 600, false))));

            when(repository.findActiveAnchors(CONTEXT_ID)).thenReturn(List.of());

            initializer.initializeContext(CONTEXT_ID);

            var nodeCaptor = ArgumentCaptor.forClass(PropositionNode.class);
            verify(repository).saveNode(nodeCaptor.capture());
            assertThat(nodeCaptor.getValue().getText()).isEqualTo("The sky is blue");
            assertThat(nodeCaptor.getValue().getContextId()).isEqualTo(CONTEXT_ID);

            verify(anchorEngine).promote(nodeCaptor.getValue().getId(), 600);
            verify(repository).setAuthority(nodeCaptor.getValue().getId(), Authority.RELIABLE.name());
        }
    }

    @Nested
    @DisplayName("idempotent on second init")
    class IdempotentOnSecondInit {

        @Test
        @DisplayName("skips existing anchors with matching text")
        void skipsExistingAnchorsOnSecondInit() {
            var initializer = initializerWith(enabledSeedConfig(
                    List.of(new DiceAnchorsProperties.ChatSeedAnchor(
                            "The sky is blue", "RELIABLE", 600, false))));

            var existingNode = new PropositionNode("the sky is blue", 0.9);
            existingNode.setContextId(CONTEXT_ID);
            when(repository.findActiveAnchors(CONTEXT_ID)).thenReturn(List.of(existingNode));

            initializer.initializeContext(CONTEXT_ID);

            verify(repository, never()).saveNode(any());
            verify(anchorEngine, never()).promote(anyString(), anyInt());
        }
    }

    @Nested
    @DisplayName("CANON seeds bypass canonization gate")
    class CanonSeeds {

        @Test
        @DisplayName("CANON authority is set directly via repository.setAuthority")
        void canonSeedsBypassGate() {
            var initializer = initializerWith(enabledSeedConfig(
                    List.of(new DiceAnchorsProperties.ChatSeedAnchor(
                            "Fundamental truth", "CANON", 800, true))));

            when(repository.findActiveAnchors(CONTEXT_ID)).thenReturn(List.of());

            initializer.initializeContext(CONTEXT_ID);

            var nodeCaptor = ArgumentCaptor.forClass(PropositionNode.class);
            verify(repository).saveNode(nodeCaptor.capture());
            verify(anchorEngine).promote(nodeCaptor.getValue().getId(), 800);
            verify(repository).setAuthority(nodeCaptor.getValue().getId(), Authority.CANON.name());
            verify(repository).updatePinned(nodeCaptor.getValue().getId(), true);
        }
    }

    @Nested
    @DisplayName("disabled config skips seeding")
    class DisabledConfig {

        @Test
        @DisplayName("null chatSeed config skips seeding")
        void nullChatSeedSkipsSeeding() {
            var initializer = initializerWith(null);

            initializer.initializeContext(CONTEXT_ID);

            verify(repository, never()).saveNode(any());
            verify(anchorEngine, never()).promote(anyString(), anyInt());
        }

        @Test
        @DisplayName("disabled chatSeed config skips seeding")
        void disabledChatSeedSkipsSeeding() {
            var disabledSeed = new DiceAnchorsProperties.ChatSeedConfig(false, List.of(
                    new DiceAnchorsProperties.ChatSeedAnchor("test", "RELIABLE", 500, false)));
            var initializer = initializerWith(disabledSeed);

            initializer.initializeContext(CONTEXT_ID);

            verify(repository, never()).saveNode(any());
            verify(anchorEngine, never()).promote(anyString(), anyInt());
        }

        @Test
        @DisplayName("enabled config with null anchors list skips seeding")
        void enabledConfigWithNullAnchorsSkips() {
            var emptySeed = new DiceAnchorsProperties.ChatSeedConfig(true, null);
            var initializer = initializerWith(emptySeed);

            initializer.initializeContext(CONTEXT_ID);

            verify(repository, never()).saveNode(any());
        }

        @Test
        @DisplayName("enabled config with empty anchors list skips seeding")
        void enabledConfigWithEmptyAnchorsSkips() {
            var emptySeed = new DiceAnchorsProperties.ChatSeedConfig(true, List.of());
            var initializer = initializerWith(emptySeed);

            initializer.initializeContext(CONTEXT_ID);

            verify(repository, never()).saveNode(any());
        }
    }

    @Nested
    @DisplayName("non-CANON seeds go through normal promote path")
    class NonCanonSeeds {

        @Test
        @DisplayName("PROVISIONAL seed does not call setAuthority")
        void provisionalSeedNoAuthorityUpgrade() {
            var initializer = initializerWith(enabledSeedConfig(
                    List.of(new DiceAnchorsProperties.ChatSeedAnchor(
                            "Provisional fact", "PROVISIONAL", 400, false))));

            when(repository.findActiveAnchors(CONTEXT_ID)).thenReturn(List.of());

            initializer.initializeContext(CONTEXT_ID);

            verify(repository).saveNode(any());
            verify(anchorEngine).promote(anyString(), eq(400));
            // PROVISIONAL has level 0, not > PROVISIONAL level, so setAuthority should NOT be called
            verify(repository, never()).setAuthority(anyString(), anyString());
            verify(repository, never()).updatePinned(anyString(), anyBoolean());
        }

        @Test
        @DisplayName("UNRELIABLE seed sets authority above PROVISIONAL")
        void unreliableSeedSetsAuthority() {
            var initializer = initializerWith(enabledSeedConfig(
                    List.of(new DiceAnchorsProperties.ChatSeedAnchor(
                            "Somewhat reliable fact", "UNRELIABLE", 450, false))));

            when(repository.findActiveAnchors(CONTEXT_ID)).thenReturn(List.of());

            initializer.initializeContext(CONTEXT_ID);

            var nodeCaptor = ArgumentCaptor.forClass(PropositionNode.class);
            verify(repository).saveNode(nodeCaptor.capture());
            verify(repository).setAuthority(nodeCaptor.getValue().getId(), Authority.UNRELIABLE.name());
        }
    }

    @Nested
    @DisplayName("pinned seeds")
    class PinnedSeeds {

        @Test
        @DisplayName("pinned flag is set via repository.updatePinned")
        void pinnedFlagSet() {
            var initializer = initializerWith(enabledSeedConfig(
                    List.of(new DiceAnchorsProperties.ChatSeedAnchor(
                            "Pinned fact", "RELIABLE", 700, true))));

            when(repository.findActiveAnchors(CONTEXT_ID)).thenReturn(List.of());

            initializer.initializeContext(CONTEXT_ID);

            var nodeCaptor = ArgumentCaptor.forClass(PropositionNode.class);
            verify(repository).saveNode(nodeCaptor.capture());
            verify(repository).updatePinned(nodeCaptor.getValue().getId(), true);
        }

        @Test
        @DisplayName("non-pinned seed does not call updatePinned")
        void nonPinnedDoesNotCallUpdatePinned() {
            var initializer = initializerWith(enabledSeedConfig(
                    List.of(new DiceAnchorsProperties.ChatSeedAnchor(
                            "Unpinned fact", "RELIABLE", 500, false))));

            when(repository.findActiveAnchors(CONTEXT_ID)).thenReturn(List.of());

            initializer.initializeContext(CONTEXT_ID);

            verify(repository, never()).updatePinned(anyString(), anyBoolean());
        }
    }

    private ChatContextInitializer initializerWith(DiceAnchorsProperties.ChatSeedConfig chatSeed) {
        var props = propertiesWithSeed(chatSeed);
        return new ChatContextInitializer(props, anchorEngine, repository);
    }

    private static DiceAnchorsProperties.ChatSeedConfig enabledSeedConfig(
            List<DiceAnchorsProperties.ChatSeedAnchor> anchors) {
        return new DiceAnchorsProperties.ChatSeedConfig(true, anchors);
    }

    private static DiceAnchorsProperties propertiesWithSeed(
            DiceAnchorsProperties.ChatSeedConfig chatSeed) {
        var anchorConfig = new DiceAnchorsProperties.AnchorConfig(
                20, 500, 100, 900, true, 0.65,
                "FAST_THEN_LLM", "TIERED",
                true, true, true,
                0.6, 400, 200, null, "hitl-only", null, null, chatSeed);
        return new DiceAnchorsProperties(
                anchorConfig,
                new DiceAnchorsProperties.ChatConfig("dm", 200, null),
                new DiceAnchorsProperties.MemoryConfig(true, null, null, "text-embedding-3-small", 20, 5, 2),
                new DiceAnchorsProperties.PersistenceConfig(false),
                new DiceAnchorsProperties.SimConfig("gpt-4.1-mini", 30, 30, 10, true, 4),
                new DiceAnchorsProperties.ConflictDetectionConfig("llm", "gpt-4o-nano"),
                new DiceAnchorsProperties.RunHistoryConfig("memory"),
                new DiceAnchorsProperties.AssemblyConfig(0),
                null, null);
    }
}
