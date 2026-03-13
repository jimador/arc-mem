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
import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.persistence.PropositionNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    @Mock private ArcMemEngine arcMemEngine;
    @Mock private MemoryUnitRepository repository;

    @Nested
    @DisplayName("seeding on first init")
    class SeedingOnFirstInit {

        @Test
        @DisplayName("creates propositions and promotes them on first init")
        void seedsCreatedOnFirstInit() {
            var initializer = initializerWith(enabledSeedConfig(
                    List.of(new ArcMemProperties.ChatSeedUnit(
                            "The sky is blue", Authority.RELIABLE, 600, false))));

            when(repository.findActiveUnits(CONTEXT_ID)).thenReturn(List.of());

            initializer.initializeContext(CONTEXT_ID);

            var nodeCaptor = ArgumentCaptor.forClass(PropositionNode.class);
            verify(repository).saveNode(nodeCaptor.capture());
            assertThat(nodeCaptor.getValue().getText()).isEqualTo("The sky is blue");
            assertThat(nodeCaptor.getValue().getContextId()).isEqualTo(CONTEXT_ID);

            verify(arcMemEngine).promote(nodeCaptor.getValue().getId(), 600);
            verify(repository).setAuthority(nodeCaptor.getValue().getId(), Authority.RELIABLE.name());
        }
    }

    @Nested
    @DisplayName("idempotent on second init")
    class IdempotentOnSecondInit {

        @Test
        @DisplayName("skips existing memory units with matching text")
        void skipsExistingUnitsOnSecondInit() {
            var initializer = initializerWith(enabledSeedConfig(
                    List.of(new ArcMemProperties.ChatSeedUnit(
                            "The sky is blue", Authority.RELIABLE, 600, false))));

            var existingNode = new PropositionNode(UUID.randomUUID().toString(), "test-context", "the sky is blue", 0.9, 0.0, null, List.of(),
                    Instant.now(), Instant.now(), PropositionStatus.ACTIVE, null, List.of());
            existingNode.setContextId(CONTEXT_ID);
            when(repository.findActiveUnits(CONTEXT_ID)).thenReturn(List.of(existingNode));

            initializer.initializeContext(CONTEXT_ID);

            verify(repository, never()).saveNode(any());
            verify(arcMemEngine, never()).promote(anyString(), anyInt());
        }
    }

    @Nested
    @DisplayName("CANON seeds bypass canonization gate")
    class CanonSeeds {

        @Test
        @DisplayName("CANON authority is set directly via repository.setAuthority")
        void canonSeedsBypassGate() {
            var initializer = initializerWith(enabledSeedConfig(
                    List.of(new ArcMemProperties.ChatSeedUnit(
                            "Fundamental truth", Authority.CANON, 800, true))));

            when(repository.findActiveUnits(CONTEXT_ID)).thenReturn(List.of());

            initializer.initializeContext(CONTEXT_ID);

            var nodeCaptor = ArgumentCaptor.forClass(PropositionNode.class);
            verify(repository).saveNode(nodeCaptor.capture());
            verify(arcMemEngine).promote(nodeCaptor.getValue().getId(), 800);
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
            verify(arcMemEngine, never()).promote(anyString(), anyInt());
        }

        @Test
        @DisplayName("disabled chatSeed config skips seeding")
        void disabledChatSeedSkipsSeeding() {
            var disabledSeed = new ArcMemProperties.ChatSeedConfig(false, List.of(
                    new ArcMemProperties.ChatSeedUnit("test", Authority.RELIABLE, 500, false)));
            var initializer = initializerWith(disabledSeed);

            initializer.initializeContext(CONTEXT_ID);

            verify(repository, never()).saveNode(any());
            verify(arcMemEngine, never()).promote(anyString(), anyInt());
        }

        @Test
        @DisplayName("enabled config with null memory units list skips seeding")
        void enabledConfigWithNullUnitsSkips() {
            var emptySeed = new ArcMemProperties.ChatSeedConfig(true, null);
            var initializer = initializerWith(emptySeed);

            initializer.initializeContext(CONTEXT_ID);

            verify(repository, never()).saveNode(any());
        }

        @Test
        @DisplayName("enabled config with empty memory units list skips seeding")
        void enabledConfigWithEmptyUnitsSkips() {
            var emptySeed = new ArcMemProperties.ChatSeedConfig(true, List.of());
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
                    List.of(new ArcMemProperties.ChatSeedUnit(
                            "Provisional fact", Authority.PROVISIONAL, 400, false))));

            when(repository.findActiveUnits(CONTEXT_ID)).thenReturn(List.of());

            initializer.initializeContext(CONTEXT_ID);

            verify(repository).saveNode(any());
            verify(arcMemEngine).promote(anyString(), eq(400));
            // PROVISIONAL has level 0, not > PROVISIONAL level, so setAuthority should NOT be called
            verify(repository, never()).setAuthority(anyString(), anyString());
            verify(repository, never()).updatePinned(anyString(), anyBoolean());
        }

        @Test
        @DisplayName("UNRELIABLE seed sets authority above PROVISIONAL")
        void unreliableSeedSetsAuthority() {
            var initializer = initializerWith(enabledSeedConfig(
                    List.of(new ArcMemProperties.ChatSeedUnit(
                            "Somewhat reliable fact", Authority.UNRELIABLE, 450, false))));

            when(repository.findActiveUnits(CONTEXT_ID)).thenReturn(List.of());

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
                    List.of(new ArcMemProperties.ChatSeedUnit(
                            "Pinned fact", Authority.RELIABLE, 700, true))));

            when(repository.findActiveUnits(CONTEXT_ID)).thenReturn(List.of());

            initializer.initializeContext(CONTEXT_ID);

            var nodeCaptor = ArgumentCaptor.forClass(PropositionNode.class);
            verify(repository).saveNode(nodeCaptor.capture());
            verify(repository).updatePinned(nodeCaptor.getValue().getId(), true);
        }

        @Test
        @DisplayName("non-pinned seed does not call updatePinned")
        void nonPinnedDoesNotCallUpdatePinned() {
            var initializer = initializerWith(enabledSeedConfig(
                    List.of(new ArcMemProperties.ChatSeedUnit(
                            "Unpinned fact", Authority.RELIABLE, 500, false))));

            when(repository.findActiveUnits(CONTEXT_ID)).thenReturn(List.of());

            initializer.initializeContext(CONTEXT_ID);

            verify(repository, never()).updatePinned(anyString(), anyBoolean());
        }
    }

    private ChatContextInitializer initializerWith(ArcMemProperties.ChatSeedConfig chatSeed) {
        var props = propertiesWithSeed(chatSeed);
        return new ChatContextInitializer(props, arcMemEngine, repository);
    }

    private static ArcMemProperties.ChatSeedConfig enabledSeedConfig(
            List<ArcMemProperties.ChatSeedUnit> units) {
        return new ArcMemProperties.ChatSeedConfig(true, units);
    }

    private static ArcMemProperties propertiesWithSeed(
            ArcMemProperties.ChatSeedConfig chatSeed) {
        var unitConfig = new ArcMemProperties.UnitConfig(
                20, 500, 100, 900, true, 0.65,
                DedupStrategy.FAST_THEN_LLM, CompliancePolicyMode.TIERED,
                true, true, true,
                0.6, 400, 200, null, null, null, chatSeed, null);
        return new ArcMemProperties(
                unitConfig,
                new ArcMemProperties.MemoryConfig(true, null, null, "text-embedding-3-small", 20, 5, 2),
                new ArcMemProperties.PersistenceConfig(false),
                new ArcMemProperties.ConflictDetectionConfig(ConflictStrategy.LLM, "gpt-4o-nano"),
                new ArcMemProperties.AssemblyConfig(0, false, EnforcementStrategy.PROMPT_ONLY),
                null, null, null, null, null, null,
                new ArcMemProperties.LlmCallConfig(30, 10));
    }
}
