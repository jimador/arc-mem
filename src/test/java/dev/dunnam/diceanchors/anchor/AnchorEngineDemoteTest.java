package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.event.AnchorLifecycleEvent;
import dev.dunnam.diceanchors.anchor.event.ArchiveReason;
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
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnchorEngine.demote()")
class AnchorEngineDemoteTest {

    private static final String CONTEXT_ID = "ctx-demote";

    @Mock private AnchorRepository repository;
    @Mock private ConflictDetector conflictDetector;
    @Mock private ConflictResolver conflictResolver;
    @Mock private ReinforcementPolicy reinforcementPolicy;
    @Mock private DecayPolicy decayPolicy;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TrustPipeline trustPipeline;
    @Mock private CanonizationGate canonizationGate;

    private AnchorEngine engine;
    private AnchorEngine engineWithGateDisabled;

    @BeforeEach
    void setUp() {
        engine = new AnchorEngine(
                repository,
                properties(true, true),
                conflictDetector,
                conflictResolver,
                reinforcementPolicy,
                decayPolicy,
                eventPublisher,
                trustPipeline,
                canonizationGate);

        engineWithGateDisabled = new AnchorEngine(
                repository,
                properties(true, false),
                conflictDetector,
                conflictResolver,
                reinforcementPolicy,
                decayPolicy,
                eventPublisher,
                trustPipeline,
                canonizationGate);
    }

    @Nested
    @DisplayName("demote RELIABLE anchor")
    class DemoteReliable {

        @Test
        @DisplayName("demotes RELIABLE to UNRELIABLE and publishes AuthorityChanged DEMOTED event")
        void demoteReliableToUnreliable() {
            var node = anchorNode("a1", Authority.RELIABLE);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));

            engine.demote("a1", DemotionReason.MANUAL);

            verify(repository).setAuthority("a1", Authority.UNRELIABLE.name());
            var captor = ArgumentCaptor.forClass(AnchorLifecycleEvent.AuthorityChanged.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().getAnchorId()).isEqualTo("a1");
            assertThat(captor.getValue().getPreviousAuthority()).isEqualTo(Authority.RELIABLE);
            assertThat(captor.getValue().getNewAuthority()).isEqualTo(Authority.UNRELIABLE);
            assertThat(captor.getValue().getDirection()).isEqualTo(AuthorityChangeDirection.DEMOTED);
        }
    }

    @Nested
    @DisplayName("demote PROVISIONAL anchor (falls through to archive)")
    class DemoteProvisional {

        @Test
        @DisplayName("archives PROVISIONAL anchor instead of demoting further")
        void provisionalAnchorArchived() {
            var node = anchorNode("a2", Authority.PROVISIONAL);
            // findPropositionNodeById called twice: once by demote(), once by archive()
            when(repository.findPropositionNodeById("a2")).thenReturn(Optional.of(node));

            engine.demote("a2", DemotionReason.MANUAL);

            verify(repository).archiveAnchor("a2");
            verify(eventPublisher).publishEvent(any(AnchorLifecycleEvent.Archived.class));
            verify(repository, never()).setAuthority(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("demote non-existent anchor")
    class DemoteNonExistent {

        @Test
        @DisplayName("logs warning and does nothing when anchor not found")
        void nonExistentAnchorNoException() {
            when(repository.findPropositionNodeById("missing")).thenReturn(Optional.empty());

            // Must not throw
            engine.demote("missing", DemotionReason.MANUAL);

            verify(repository, never()).setAuthority(anyString(), anyString());
            verify(repository, never()).archiveAnchor(anyString());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("demote CANON anchor with gate enabled")
    class DemoteCanonGated {

        @Test
        @DisplayName("routes CANON demotion through canonizationGate when gate is enabled")
        void canonDemotionRoutedToGate() {
            var node = anchorNode("a3", Authority.CANON);
            when(repository.findPropositionNodeById("a3")).thenReturn(Optional.of(node));

            engine.demote("a3", DemotionReason.MANUAL);

            verify(canonizationGate).requestDecanonization(
                    "a3", CONTEXT_ID, node.getText(), DemotionReason.MANUAL.name(), "system");
            verify(repository, never()).setAuthority(anyString(), anyString());
            verify(repository, never()).archiveAnchor(anyString());
        }

        @Test
        @DisplayName("demotes CANON directly when gate is disabled")
        void canonDemotedDirectlyWhenGateDisabled() {
            var node = anchorNode("a4", Authority.CANON);
            when(repository.findPropositionNodeById("a4")).thenReturn(Optional.of(node));

            engineWithGateDisabled.demote("a4", DemotionReason.MANUAL);

            verify(repository).setAuthority("a4", Authority.RELIABLE.name());
            verify(canonizationGate, never()).requestDecanonization(any(), any(), any(), any(), any());
        }
    }

    // ========================================================================
    // Private helpers
    // ========================================================================

    private PropositionNode anchorNode(String id, Authority authority) {
        var node = new PropositionNode("anchor text " + id, 0.9);
        node.setId(id);
        node.setContextId(CONTEXT_ID);
        node.setText("anchor text " + id);
        node.setRank(500);
        node.setAuthority(authority.name());
        return node;
    }

    private static DiceAnchorsProperties properties(boolean lifecycleEventsEnabled, boolean canonizationGateEnabled) {
        var anchorConfig = new DiceAnchorsProperties.AnchorConfig(
                20, 500, 100, 900, true, 0.65,
                "FAST_THEN_LLM", "TIERED",
                lifecycleEventsEnabled,
                canonizationGateEnabled,
                true,
                0.6, 400, 200);
        return new DiceAnchorsProperties(
                anchorConfig,
                new DiceAnchorsProperties.ChatConfig("dm", 200, null),
                new DiceAnchorsProperties.MemoryConfig(true, null, null, "text-embedding-3-small", 20, 5, 2),
                new DiceAnchorsProperties.PersistenceConfig(false),
                new DiceAnchorsProperties.SimConfig("gpt-4.1-mini", 30, 30, 10, true),
                new DiceAnchorsProperties.ConflictDetectionConfig("llm", "gpt-4o-nano"),
                new DiceAnchorsProperties.RunHistoryConfig("memory"),
                new DiceAnchorsProperties.AssemblyConfig(0));
    }
}
