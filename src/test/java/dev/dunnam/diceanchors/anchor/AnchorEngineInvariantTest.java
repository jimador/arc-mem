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
            var promotedNode = anchorNode("new-1", Authority.PROVISIONAL);
            promotedNode.setRank(500);

            var proposition = mock(Proposition.class);
            when(proposition.getContextIdValue()).thenReturn(CONTEXT_ID);
            when(repository.findById("new-1")).thenReturn(proposition);

            var protectedNode = anchorNode("protected-1", Authority.RELIABLE);
            protectedNode.setRank(100);
            var normalNode = anchorNode("normal-1", Authority.PROVISIONAL);
            normalNode.setRank(150);

            when(repository.findActiveAnchors(CONTEXT_ID))
                    .thenReturn(List.of(promotedNode, protectedNode, normalNode));

            var protectedViolation = new InvariantViolationData(
                    "ei-1", InvariantStrength.MUST, ProposedAction.EVICT, "Immune", "protected-1");
            var blockedEval = new InvariantEvaluation(List.of(protectedViolation), 1);
            var cleanEval = new InvariantEvaluation(List.of(), 0);

            when(invariantEvaluator.evaluate(eq(CONTEXT_ID), eq(ProposedAction.EVICT), any(), any()))
                    .thenAnswer(inv -> {
                        Anchor target = inv.getArgument(3);
                        if (target != null && target.id().equals("protected-1")) {
                            return blockedEval;
                        }
                        return cleanEval;
                    });

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

            verify(repository, never()).archiveAnchor(eq("protected-1"));
            verify(repository).archiveAnchor("normal-1");
        }
    }

    @Nested
    @DisplayName("Reinforce hook order: invariant before authority upgrade")
    class ReinforceHookOrder {

        @Test
        @DisplayName("reinforce with blocking invariant still updates rank but skips authority upgrade")
        void reinforceBlockedByInvariantUpdatesRankButSkipsAuthority() {
            var node = anchorNode("a5", Authority.UNRELIABLE);
            node.setRank(500);
            node.setReinforcementCount(7);
            when(repository.findPropositionNodeById("a5")).thenReturn(Optional.of(node));
            when(repository.findActiveAnchors(CONTEXT_ID)).thenReturn(List.of(node));
            when(reinforcementPolicy.calculateRankBoost(any())).thenReturn(50);
            when(reinforcementPolicy.shouldUpgradeAuthority(any())).thenReturn(true);

            var violation = new InvariantViolationData(
                    "ahk-1", InvariantStrength.MUST, ProposedAction.AUTHORITY_CHANGE,
                    "Authority upgrade blocked by invariant", "a5");
            var blockedEval = new InvariantEvaluation(List.of(violation), 1);
            when(invariantEvaluator.evaluate(eq(CONTEXT_ID), eq(ProposedAction.AUTHORITY_CHANGE), any(), any()))
                    .thenReturn(blockedEval);

            engine.reinforce("a5");

            // Rank is updated despite the blocked authority upgrade
            verify(repository).updateRank("a5", 550);
            // Authority is NOT upgraded
            verify(repository, never()).setAuthority(eq("a5"), anyString());
            // InvariantViolation event is published
            verify(eventPublisher).publishEvent(any(AnchorLifecycleEvent.InvariantViolation.class));
            // Reinforced event still fires (with updated rank)
            verify(eventPublisher).publishEvent(any(AnchorLifecycleEvent.Reinforced.class));
        }

        @Test
        @DisplayName("reinforce without blocking invariant proceeds with authority upgrade")
        void reinforceWithoutBlockingInvariantUpgradesAuthority() {
            var node = anchorNode("a6", Authority.UNRELIABLE);
            node.setRank(500);
            node.setReinforcementCount(7);
            when(repository.findPropositionNodeById("a6")).thenReturn(Optional.of(node));
            when(repository.findActiveAnchors(CONTEXT_ID)).thenReturn(List.of(node));
            when(reinforcementPolicy.calculateRankBoost(any())).thenReturn(50);
            when(reinforcementPolicy.shouldUpgradeAuthority(any())).thenReturn(true);

            var cleanEval = new InvariantEvaluation(List.of(), 1);
            when(invariantEvaluator.evaluate(eq(CONTEXT_ID), eq(ProposedAction.AUTHORITY_CHANGE), any(), any()))
                    .thenReturn(cleanEval);

            var trustScore = new TrustScore(0.9, Authority.RELIABLE,
                    PromotionZone.AUTO_PROMOTE, java.util.Map.of(), java.time.Instant.now());
            when(trustPipeline.evaluate(any(), any())).thenReturn(trustScore);

            engine.reinforce("a6");

            verify(repository).setAuthority("a6", Authority.RELIABLE.name());
            verify(repository).updateRank("a6", 550);
        }

        @Test
        @DisplayName("reinforce respects persisted authority ceiling on upgrade")
        void reinforceRespectsPersistedAuthorityCeiling() {
            var node = anchorNode("a7", Authority.UNRELIABLE);
            node.setRank(500);
            node.setReinforcementCount(7);
            node.setAuthorityCeiling(Authority.UNRELIABLE.name());
            when(repository.findPropositionNodeById("a7")).thenReturn(Optional.of(node));
            when(repository.findActiveAnchors(CONTEXT_ID)).thenReturn(List.of(node));
            when(reinforcementPolicy.calculateRankBoost(any())).thenReturn(50);
            when(reinforcementPolicy.shouldUpgradeAuthority(any())).thenReturn(true);

            var cleanEval = new InvariantEvaluation(List.of(), 1);
            when(invariantEvaluator.evaluate(eq(CONTEXT_ID), eq(ProposedAction.AUTHORITY_CHANGE), any(), any()))
                    .thenReturn(cleanEval);

            var trustScore = new TrustScore(0.9, Authority.RELIABLE,
                    PromotionZone.AUTO_PROMOTE, java.util.Map.of(), java.time.Instant.now());
            when(trustPipeline.evaluate(any(), any())).thenReturn(trustScore);

            engine.reinforce("a7");

            verify(repository, never()).setAuthority("a7", Authority.RELIABLE.name());
            verify(repository).updateRank("a7", 550);
        }
    }

    @Nested
    @DisplayName("Promotion persists authority ceiling")
    class PromotionPersistsAuthorityCeiling {

        @Test
        @DisplayName("promote stores ceiling on proposition node")
        void promoteStoresAuthorityCeiling() {
            engine.promote("p7", 500, Authority.UNRELIABLE);

            verify(repository).promoteToAnchor(
                    "p7",
                    500,
                    Authority.PROVISIONAL.name(),
                    MemoryTier.WARM.name(),
                    Authority.UNRELIABLE.name());
        }
    }

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
                0.6, 400, 200, null, "hitl-only", null, null, null);
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
