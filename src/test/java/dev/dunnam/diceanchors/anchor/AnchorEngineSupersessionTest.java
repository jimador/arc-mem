package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.event.AnchorLifecycleEvent;
import dev.dunnam.diceanchors.anchor.event.ArchiveReason;
import dev.dunnam.diceanchors.anchor.event.SupersessionReason;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnchorEngine supersession and temporal queries")
class AnchorEngineSupersessionTest {

    private static final String CONTEXT_ID = "ctx-1";

    @Mock
    private AnchorRepository repository;

    @Mock
    private ConflictDetector conflictDetector;

    @Mock
    private ConflictResolver conflictResolver;

    @Mock
    private ReinforcementPolicy reinforcementPolicy;

    @Mock
    private DecayPolicy decayPolicy;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TrustPipeline trustPipeline;

    @Mock
    private CanonizationGate canonizationGate;

    @Mock
    private InvariantEvaluator invariantEvaluator;

    private AnchorEngine engine;

    @BeforeEach
    void setUp() {
        lenient().when(invariantEvaluator.evaluate(any(), any(), any(), any()))
                .thenReturn(new InvariantEvaluation(List.of(), 0));

        engine = new AnchorEngine(
                repository,
                properties(),
                conflictDetector,
                conflictResolver,
                reinforcementPolicy,
                decayPolicy,
                eventPublisher,
                trustPipeline,
                canonizationGate,
                invariantEvaluator);
    }

    @Nested
    @DisplayName("supersede flow (7.4)")
    class SupersedeFlow {

        @Test
        @DisplayName("supersede archives predecessor with successor reference")
        void supersedeArchivesPredecessorWithSuccessor() {
            when(repository.findPropositionNodeById("pred-1"))
                    .thenReturn(Optional.of(anchorNode("pred-1", 500, Authority.RELIABLE)));

            engine.supersede("pred-1", "succ-2", ArchiveReason.CONFLICT_REPLACEMENT);

            verify(repository).archiveAnchor("pred-1", "succ-2");
        }

        @Test
        @DisplayName("supersede creates supersession link with correct reason")
        void supersedeCreatesSupersessionLinkWithCorrectReason() {
            when(repository.findPropositionNodeById("pred-1"))
                    .thenReturn(Optional.of(anchorNode("pred-1", 400, Authority.UNRELIABLE)));

            engine.supersede("pred-1", "succ-2", ArchiveReason.CONFLICT_REPLACEMENT);

            verify(repository).createSupersessionLink("succ-2", "pred-1",
                    SupersessionReason.CONFLICT_REPLACEMENT);
        }

        @Test
        @DisplayName("supersede maps DORMANCY_DECAY to DECAY_DEMOTION for supersession link")
        void supersedeMapsDecayReasonCorrectly() {
            when(repository.findPropositionNodeById("pred-1"))
                    .thenReturn(Optional.of(anchorNode("pred-1", 300, Authority.PROVISIONAL)));

            engine.supersede("pred-1", "succ-2", ArchiveReason.DORMANCY_DECAY);

            verify(repository).createSupersessionLink("succ-2", "pred-1",
                    SupersessionReason.DECAY_DEMOTION);
        }

        @Test
        @DisplayName("supersede publishes Superseded event with correct fields")
        void supersedePublishesSupersededEvent() {
            when(repository.findPropositionNodeById("pred-1"))
                    .thenReturn(Optional.of(anchorNode("pred-1", 500, Authority.RELIABLE)));

            engine.supersede("pred-1", "succ-2", ArchiveReason.CONFLICT_REPLACEMENT);

            var captor = ArgumentCaptor.forClass(AnchorLifecycleEvent.Superseded.class);
            // Two events: Archived (from archive()) and Superseded
            var eventCaptor = ArgumentCaptor.forClass(AnchorLifecycleEvent.class);
            verify(eventPublisher, org.mockito.Mockito.atLeast(1)).publishEvent(eventCaptor.capture());

            var supersededEvents = eventCaptor.getAllValues().stream()
                    .filter(e -> e instanceof AnchorLifecycleEvent.Superseded)
                    .map(e -> (AnchorLifecycleEvent.Superseded) e)
                    .toList();

            assertThat(supersededEvents).hasSize(1);
            var event = supersededEvents.getFirst();
            assertThat(event.getPredecessorId()).isEqualTo("pred-1");
            assertThat(event.getSuccessorId()).isEqualTo("succ-2");
            assertThat(event.getReason()).isEqualTo(SupersessionReason.CONFLICT_REPLACEMENT);
            assertThat(event.getContextId()).isEqualTo(CONTEXT_ID);
        }

        @Test
        @DisplayName("supersede is no-op when predecessor not found")
        void supersedeNoOpWhenPredecessorNotFound() {
            when(repository.findPropositionNodeById("missing"))
                    .thenReturn(Optional.empty());

            engine.supersede("missing", "succ-2", ArchiveReason.CONFLICT_REPLACEMENT);

            verify(repository, never()).archiveAnchor(any(), any());
            verify(repository, never()).createSupersessionLink(any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("Temporal query delegation (7.5)")
    class TemporalQueryDelegation {

        @Test
        @DisplayName("findValidAt delegates to repository")
        void findValidAtDelegatesToRepository() {
            var pointInTime = Instant.parse("2026-03-01T12:00:00Z");
            when(repository.findValidAt(CONTEXT_ID, pointInTime))
                    .thenReturn(List.of("a1", "a2"));

            var result = engine.findValidAt(CONTEXT_ID, pointInTime);

            assertThat(result).containsExactly("a1", "a2");
            verify(repository).findValidAt(CONTEXT_ID, pointInTime);
        }

        @Test
        @DisplayName("findPredecessor delegates to repository")
        void findPredecessorDelegatesToRepository() {
            when(repository.findPredecessor("a1"))
                    .thenReturn(Optional.of("a0"));

            var result = engine.findPredecessor("a1");

            assertThat(result).contains("a0");
            verify(repository).findPredecessor("a1");
        }

        @Test
        @DisplayName("findPredecessor returns empty when no predecessor")
        void findPredecessorReturnsEmptyWhenNone() {
            when(repository.findPredecessor("a1"))
                    .thenReturn(Optional.empty());

            var result = engine.findPredecessor("a1");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("findSuccessor delegates to repository")
        void findSuccessorDelegatesToRepository() {
            when(repository.findSuccessor("a1"))
                    .thenReturn(Optional.of("a2"));

            var result = engine.findSuccessor("a1");

            assertThat(result).contains("a2");
            verify(repository).findSuccessor("a1");
        }

        @Test
        @DisplayName("findSuccessor returns empty when not superseded")
        void findSuccessorReturnsEmptyWhenNone() {
            when(repository.findSuccessor("a1"))
                    .thenReturn(Optional.empty());

            var result = engine.findSuccessor("a1");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("findSupersessionChain delegates to repository")
        void findSupersessionChainDelegatesToRepository() {
            when(repository.findSupersessionChain("a2"))
                    .thenReturn(List.of("a0", "a1", "a2"));

            var result = engine.findSupersessionChain("a2");

            assertThat(result).containsExactly("a0", "a1", "a2");
            verify(repository).findSupersessionChain("a2");
        }
    }

    @Nested
    @DisplayName("Budget eviction does not create supersession link (7.6)")
    class BudgetEvictionNoSupersession {

        @Test
        @DisplayName("promote with eviction does not call createSupersessionLink")
        void promoteWithEvictionDoesNotCreateSupersessionLink() {
            var proposition = org.mockito.Mockito.mock(com.embabel.dice.proposition.Proposition.class);
            when(proposition.getContextIdValue()).thenReturn(CONTEXT_ID);
            when(repository.findById("p1")).thenReturn(proposition);
            when(repository.evictLowestRanked(CONTEXT_ID, 20))
                    .thenReturn(List.of(new EvictedAnchorInfo("evicted-1", 150)));

            engine.promote("p1", 500);

            verify(repository, never()).createSupersessionLink(any(), any(), any());
        }
    }

    // ── Test helpers ────────────────────────────────────────────────────────────

    private static PropositionNode anchorNode(String id, int rank, Authority authority) {
        var node = new PropositionNode("text", 0.9);
        node.setId(id);
        node.setContextId(CONTEXT_ID);
        node.setText("anchor text " + id);
        node.setRank(rank);
        node.setAuthority(authority.name());
        node.setReinforcementCount(0);
        return node;
    }

    private static DiceAnchorsProperties properties() {
        var anchorConfig = new DiceAnchorsProperties.AnchorConfig(
                20,
                500,
                100,
                900,
                true,
                0.65,
                "FAST_THEN_LLM",
                "TIERED",
                true,
                true,
                true,
                0.6,
                400,
                200,
                null, null, null);
        return new DiceAnchorsProperties(
                anchorConfig,
                new DiceAnchorsProperties.ChatConfig("dm", 200, null),
                new DiceAnchorsProperties.MemoryConfig(true, null, null, "text-embedding-3-small", 20, 5, 2),
                new DiceAnchorsProperties.PersistenceConfig(false),
                new DiceAnchorsProperties.SimConfig("gpt-4.1-mini", 30, 30, 10, true),
                new DiceAnchorsProperties.ConflictDetectionConfig("llm", "gpt-4o-nano"),
                new DiceAnchorsProperties.RunHistoryConfig("memory"),
                new DiceAnchorsProperties.AssemblyConfig(0),
                null, null);
    }
}
