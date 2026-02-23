package dev.dunnam.diceanchors.anchor;

import com.embabel.dice.proposition.Proposition;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnchorEngine — invariant enforcement")
class AnchorEngineInvariantTest {

    private static final String CONTEXT_ID = "ctx-inv";

    @Mock private AnchorRepository repository;
    @Mock private ConflictDetector conflictDetector;
    @Mock private ConflictResolver conflictResolver;
    @Mock private ReinforcementPolicy reinforcementPolicy;
    @Mock private DecayPolicy decayPolicy;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TrustPipeline trustPipeline;
    @Mock private CanonizationGate canonizationGate;
    @Mock private InvariantEvaluator invariantEvaluator;

    private AnchorEngine engine;

    @BeforeEach
    void setUp() {
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
    @DisplayName("archive blocked by MUST invariant")
    class ArchiveBlockedByMust {

        @Test
        @DisplayName("archive is blocked and repository.archiveAnchor is NOT called")
        void archiveBlockedByMustInvariant() {
            var node = anchorNode("a1", Authority.RELIABLE);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));
            when(repository.findActiveAnchors(CONTEXT_ID)).thenReturn(List.of(node));

            var violation = new InvariantViolationData(
                    "ap-1", InvariantStrength.MUST, ProposedAction.ARCHIVE, "Blocked", "a1");
            var eval = new InvariantEvaluation(List.of(violation), 1);
            when(invariantEvaluator.evaluate(eq(CONTEXT_ID), eq(ProposedAction.ARCHIVE), any(), any()))
                    .thenReturn(eval);

            engine.archive("a1", ArchiveReason.MANUAL);

            verify(repository, never()).archiveAnchor(anyString(), any());
            verify(eventPublisher).publishEvent(any(AnchorLifecycleEvent.InvariantViolation.class));
        }
    }

    @Nested
    @DisplayName("archive warned by SHOULD invariant")
    class ArchiveWarnedByShould {

        @Test
        @DisplayName("archive proceeds with warning and repository.archiveAnchor IS called")
        void archiveWarnedByShouldInvariant() {
            var node = anchorNode("a2", Authority.RELIABLE);
            when(repository.findPropositionNodeById("a2")).thenReturn(Optional.of(node));
            when(repository.findActiveAnchors(CONTEXT_ID)).thenReturn(List.of(node));

            var violation = new InvariantViolationData(
                    "ap-2", InvariantStrength.SHOULD, ProposedAction.ARCHIVE, "Warning", "a2");
            var eval = new InvariantEvaluation(List.of(violation), 1);
            when(invariantEvaluator.evaluate(eq(CONTEXT_ID), eq(ProposedAction.ARCHIVE), any(), any()))
                    .thenReturn(eval);

            engine.archive("a2", ArchiveReason.MANUAL);

            verify(repository).archiveAnchor("a2", null);
            verify(eventPublisher).publishEvent(any(AnchorLifecycleEvent.InvariantViolation.class));
            verify(eventPublisher).publishEvent(any(AnchorLifecycleEvent.Archived.class));
        }
    }

    @Nested
    @DisplayName("demotion blocked by AuthorityFloor MUST")
    class DemotionBlockedByAuthorityFloor {

        @Test
        @DisplayName("demote is blocked and repository.setAuthority is NOT called")
        void demoteBlockedByAuthorityFloor() {
            var node = anchorNode("a3", Authority.RELIABLE);
            when(repository.findPropositionNodeById("a3")).thenReturn(Optional.of(node));
            when(repository.findActiveAnchors(CONTEXT_ID)).thenReturn(List.of(node));

            var violation = new InvariantViolationData(
                    "af-1", InvariantStrength.MUST, ProposedAction.DEMOTE,
                    "Authority floor violation", "a3");
            var eval = new InvariantEvaluation(List.of(violation), 1);
            when(invariantEvaluator.evaluate(eq(CONTEXT_ID), eq(ProposedAction.DEMOTE), any(), any()))
                    .thenReturn(eval);

            engine.demote("a3", DemotionReason.MANUAL);

            verify(repository, never()).setAuthority(anyString(), anyString());
            verify(eventPublisher).publishEvent(any(AnchorLifecycleEvent.InvariantViolation.class));
        }
    }

    @Nested
    @DisplayName("MinAuthorityCount blocks archive when count would drop below minimum")
    class MinAuthorityCountBlocks {

        @Test
        @DisplayName("archive is blocked when count would drop below minimum")
        void archiveBlockedByMinAuthorityCount() {
            var node = anchorNode("a4", Authority.RELIABLE);
            when(repository.findPropositionNodeById("a4")).thenReturn(Optional.of(node));
            when(repository.findActiveAnchors(CONTEXT_ID)).thenReturn(List.of(node));

            var violation = new InvariantViolationData(
                    "mac-1", InvariantStrength.MUST, ProposedAction.ARCHIVE,
                    "Count would drop below minimum", "a4");
            var eval = new InvariantEvaluation(List.of(violation), 1);
            when(invariantEvaluator.evaluate(eq(CONTEXT_ID), eq(ProposedAction.ARCHIVE), any(), any()))
                    .thenReturn(eval);

            engine.archive("a4", ArchiveReason.MANUAL);

            verify(repository, never()).archiveAnchor(anyString(), any());
        }
    }

    @Nested
    @DisplayName("eviction skips immune candidates during promote")
    class EvictionSkipsImmune {

        @Test
        @DisplayName("invariant-protected anchors are not evicted during budget enforcement")
        void invariantProtectedAnchorsNotEvicted() {
            // Set up: promote triggers eviction check
            var promotedNode = anchorNode("new-1", Authority.PROVISIONAL);
            promotedNode.setRank(500);

            // Mock the DICE Proposition returned by findById
            var proposition = mock(Proposition.class);
            when(proposition.getContextIdValue()).thenReturn(CONTEXT_ID);
            when(repository.findById("new-1")).thenReturn(proposition);

            // Two existing anchors, one protected by invariant
            var protectedNode = anchorNode("protected-1", Authority.RELIABLE);
            protectedNode.setRank(100); // lowest rank, would normally be evicted
            var normalNode = anchorNode("normal-1", Authority.PROVISIONAL);
            normalNode.setRank(150);

            when(repository.findActiveAnchors(CONTEXT_ID))
                    .thenReturn(List.of(promotedNode, protectedNode, normalNode));

            // For protected anchor: MUST violation blocks eviction
            var protectedViolation = new InvariantViolationData(
                    "ei-1", InvariantStrength.MUST, ProposedAction.EVICT, "Immune", "protected-1");
            var blockedEval = new InvariantEvaluation(List.of(protectedViolation), 1);
            var cleanEval = new InvariantEvaluation(List.of(), 0);

            // The evaluator is called for each non-pinned anchor during eviction
            when(invariantEvaluator.evaluate(eq(CONTEXT_ID), eq(ProposedAction.EVICT), any(), any()))
                    .thenAnswer(inv -> {
                        Anchor target = inv.getArgument(3);
                        if (target != null && target.id().equals("protected-1")) {
                            return blockedEval;
                        }
                        return cleanEval;
                    });

            // Use budget of 2 so one must be evicted from the 3
            var engineSmallBudget = new AnchorEngine(
                    repository,
                    propertiesWithBudget(2),
                    conflictDetector,
                    conflictResolver,
                    reinforcementPolicy,
                    decayPolicy,
                    eventPublisher,
                    trustPipeline,
                    canonizationGate,
                    invariantEvaluator);

            engineSmallBudget.promote("new-1", 500);

            // Protected anchor should NOT be archived (evicted)
            verify(repository, never()).archiveAnchor(eq("protected-1"));
            // Normal anchor should be archived (evicted) since it's the lowest non-protected
            verify(repository).archiveAnchor("normal-1");
        }
    }

    // ========================================================================
    // Helpers
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

    private static DiceAnchorsProperties properties() {
        return propertiesWithBudget(20);
    }

    private static DiceAnchorsProperties propertiesWithBudget(int budget) {
        var anchorConfig = new DiceAnchorsProperties.AnchorConfig(
                budget, 500, 100, 900, true, 0.65,
                "FAST_THEN_LLM", "TIERED",
                true, true, true,
                0.6, 400, 200, null, null, null);
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
