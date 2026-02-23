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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnchorEngine lifecycle event publishing")
class AnchorEngineLifecycleEventsTest {

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

    private AnchorEngine enabledEngine;
    private AnchorEngine disabledEngine;

    @BeforeEach
    void setUp() {
        lenient().when(invariantEvaluator.evaluate(any(), any(), any(), any()))
                .thenReturn(new InvariantEvaluation(List.of(), 0));

        enabledEngine = new AnchorEngine(
                repository,
                properties(true),
                conflictDetector,
                conflictResolver,
                reinforcementPolicy,
                decayPolicy,
                eventPublisher,
                trustPipeline,
                canonizationGate,
                invariantEvaluator);

        disabledEngine = new AnchorEngine(
                repository,
                properties(false),
                conflictDetector,
                conflictResolver,
                reinforcementPolicy,
                decayPolicy,
                eventPublisher,
                trustPipeline,
                canonizationGate,
                invariantEvaluator);
    }

    @Test
    @DisplayName("promote publishes AnchorPromotedEvent")
    void promotePublishesPromotedEvent() {
        var proposition = org.mockito.Mockito.mock(Proposition.class);
        when(proposition.getContextIdValue()).thenReturn(CONTEXT_ID);
        when(repository.findById("p1")).thenReturn(proposition);

        enabledEngine.promote("p1", 500);

        var captor = ArgumentCaptor.forClass(AnchorLifecycleEvent.Promoted.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getContextId()).isEqualTo(CONTEXT_ID);
        assertThat(captor.getValue().getAnchorId()).isEqualTo("p1");
        assertThat(captor.getValue().getInitialRank()).isEqualTo(500);
    }

    @Test
    @DisplayName("reinforce publishes AnchorReinforcedEvent")
    void reinforcePublishesReinforcedEvent() {
        var node = anchorNode("a1", 500, Authority.PROVISIONAL, 3);
        when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));
        when(reinforcementPolicy.calculateRankBoost(any())).thenReturn(50);
        when(reinforcementPolicy.shouldUpgradeAuthority(any())).thenReturn(false);

        enabledEngine.reinforce("a1");

        var captor = ArgumentCaptor.forClass(AnchorLifecycleEvent.Reinforced.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getAnchorId()).isEqualTo("a1");
        assertThat(captor.getValue().getPreviousRank()).isEqualTo(500);
        assertThat(captor.getValue().getNewRank()).isEqualTo(550);
        assertThat(captor.getValue().getReinforcementCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("reinforce publishes AuthorityChanged event when threshold met")
    void reinforcePublishesAuthorityChangedEventWhenThresholdMet() {
        var node = anchorNode("a1", 500, Authority.PROVISIONAL, 4);
        when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));
        when(reinforcementPolicy.calculateRankBoost(any())).thenReturn(25);
        when(reinforcementPolicy.shouldUpgradeAuthority(any())).thenReturn(true);
        // reEvaluateTrust is called before authority upgrade; stub to return a score
        // with ceiling >= current authority so no demotion occurs
        var trustScore = new TrustScore(0.9, Authority.RELIABLE,
                PromotionZone.AUTO_PROMOTE,
                Map.of(), Instant.now());
        when(trustPipeline.evaluate(any(), any())).thenReturn(trustScore);

        enabledEngine.reinforce("a1");

        verify(eventPublisher).publishEvent(any(AnchorLifecycleEvent.AuthorityChanged.class));
        verify(eventPublisher).publishEvent(any(AnchorLifecycleEvent.Reinforced.class));
    }

    @Test
    @DisplayName("detectConflicts publishes ConflictDetectedEvent when conflicts exist")
    void detectConflictsPublishesEventWhenConflictsExist() {
        when(repository.findActiveAnchors(CONTEXT_ID)).thenReturn(List.of(anchorNode("a1", 500, Authority.RELIABLE, 0)));
        var conflict = new ConflictDetector.Conflict(
                Anchor.withoutTrust("a1", "existing", 500, Authority.RELIABLE, false, 0.9, 0),
                "incoming",
                0.9,
                "negation");
        when(conflictDetector.detect(eq("incoming"), any())).thenReturn(List.of(conflict));

        enabledEngine.detectConflicts(CONTEXT_ID, "incoming");

        var captor = ArgumentCaptor.forClass(AnchorLifecycleEvent.ConflictDetected.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getConflictCount()).isEqualTo(1);
        assertThat(captor.getValue().getConflictingAnchorIds()).containsExactly("a1");
    }

    @Test
    @DisplayName("detectConflicts does not publish when no conflicts")
    void detectConflictsDoesNotPublishWhenNoConflicts() {
        when(repository.findActiveAnchors(CONTEXT_ID)).thenReturn(List.of(anchorNode("a1", 500, Authority.RELIABLE, 0)));
        when(conflictDetector.detect(eq("incoming"), any())).thenReturn(List.of());

        enabledEngine.detectConflicts(CONTEXT_ID, "incoming");

        verify(eventPublisher, never()).publishEvent(any(AnchorLifecycleEvent.ConflictDetected.class));
    }

    @Test
    @DisplayName("resolveConflict publishes ConflictResolvedEvent")
    void resolveConflictPublishesEvent() {
        var conflict = new ConflictDetector.Conflict(
                Anchor.withoutTrust("a1", "existing", 500, Authority.RELIABLE, false, 0.9, 0),
                "incoming",
                0.9,
                "negation");
        when(conflictResolver.resolve(conflict)).thenReturn(ConflictResolver.Resolution.COEXIST);
        when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(anchorNode("a1", 500, Authority.RELIABLE, 0)));

        enabledEngine.resolveConflict(conflict);

        var captor = ArgumentCaptor.forClass(AnchorLifecycleEvent.ConflictResolved.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getExistingAnchorId()).isEqualTo("a1");
        assertThat(captor.getValue().getResolution()).isEqualTo(ConflictResolver.Resolution.COEXIST);
    }

    @Test
    @DisplayName("events are not published when lifecycle events are disabled")
    void eventsNotPublishedWhenDisabled() {
        var proposition = org.mockito.Mockito.mock(Proposition.class);
        when(proposition.getContextIdValue()).thenReturn(CONTEXT_ID);
        when(repository.findById("p1")).thenReturn(proposition);

        disabledEngine.promote("p1", 500);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("archive publishes AnchorArchivedEvent with reason")
    void archivePublishesArchivedEventWithReason() {
        when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(anchorNode("a1", 500, Authority.RELIABLE, 0)));

        enabledEngine.archive("a1", ArchiveReason.DORMANCY_DECAY);

        verify(repository).archiveAnchor("a1", null);
        verify(eventPublisher).publishEvent(any(AnchorLifecycleEvent.Archived.class));
    }

    @Test
    @DisplayName("promote within budget publishes no Evicted event")
    void promoteWithinBudgetNoEvictedEvent() {
        var proposition = org.mockito.Mockito.mock(com.embabel.dice.proposition.Proposition.class);
        when(proposition.getContextIdValue()).thenReturn(CONTEXT_ID);
        when(repository.findById("p1")).thenReturn(proposition);
        // evictLowestRanked returns empty list (no eviction needed)
        when(repository.evictLowestRanked(CONTEXT_ID, 20)).thenReturn(List.of());

        enabledEngine.promote("p1", 500);

        verify(eventPublisher, never()).publishEvent(any(AnchorLifecycleEvent.Evicted.class));
    }

    @Test
    @DisplayName("promote over budget publishes Evicted event with correct anchorId and previousRank")
    void promoteOverBudgetPublishesEvictedEvent() {
        var proposition = org.mockito.Mockito.mock(com.embabel.dice.proposition.Proposition.class);
        when(proposition.getContextIdValue()).thenReturn(CONTEXT_ID);
        when(repository.findById("p1")).thenReturn(proposition);
        when(repository.evictLowestRanked(CONTEXT_ID, 20))
                .thenReturn(List.of(new dev.dunnam.diceanchors.anchor.EvictedAnchorInfo("ev-1", 150)));

        enabledEngine.promote("p1", 500);

        var captor = ArgumentCaptor.forClass(AnchorLifecycleEvent.Evicted.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getAnchorId()).isEqualTo("ev-1");
        assertThat(captor.getValue().getPreviousRank()).isEqualTo(150);
    }

    @Test
    @DisplayName("Promoted event fires before Evicted event")
    void promotedEventFiresBeforeEvictedEvent() {
        var proposition = org.mockito.Mockito.mock(com.embabel.dice.proposition.Proposition.class);
        when(proposition.getContextIdValue()).thenReturn(CONTEXT_ID);
        when(repository.findById("p1")).thenReturn(proposition);
        when(repository.evictLowestRanked(CONTEXT_ID, 20))
                .thenReturn(List.of(new dev.dunnam.diceanchors.anchor.EvictedAnchorInfo("ev-1", 150)));

        var publishedEvents = new java.util.ArrayList<AnchorLifecycleEvent>();
        org.mockito.Mockito.doAnswer(inv -> {
            publishedEvents.add(inv.getArgument(0));
            return null;
        }).when(eventPublisher).publishEvent(any(AnchorLifecycleEvent.class));

        enabledEngine.promote("p1", 500);

        assertThat(publishedEvents).hasSize(2);
        assertThat(publishedEvents.get(0)).isInstanceOf(AnchorLifecycleEvent.Promoted.class);
        assertThat(publishedEvents.get(1)).isInstanceOf(AnchorLifecycleEvent.Evicted.class);
    }

    @Nested
    @DisplayName("Trust re-evaluation triggers")
    class TrustReEvaluationTriggers {

        @Test
        @DisplayName("reinforcement milestone triggers trust re-evaluation")
        void reinforcementMilestoneTriggersTrustReEvaluation() {
            // UNRELIABLE threshold is 7 — stub node with reinforcementCount=7 (post-increment state)
            var node = anchorNode("a1", 500, Authority.UNRELIABLE, 7);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));
            when(reinforcementPolicy.calculateRankBoost(any())).thenReturn(50);
            when(reinforcementPolicy.shouldUpgradeAuthority(any())).thenReturn(true);
            // reEvaluateTrust calls findPropositionNodeById again; return same node
            var trustScore = new TrustScore(0.9, Authority.RELIABLE,
                    PromotionZone.AUTO_PROMOTE, Map.of(), java.time.Instant.now());
            when(trustPipeline.evaluate(any(), any())).thenReturn(trustScore);

            enabledEngine.reinforce("a1");

            verify(trustPipeline).evaluate(any(), any());
        }

        @Test
        @DisplayName("non-milestone reinforcement does not trigger trust re-evaluation")
        void nonMilestoneReinforcementDoesNotTriggerTrustReEvaluation() {
            // reinforcementCount=4 — UNRELIABLE threshold is 7, not reached yet
            var node = anchorNode("a1", 500, Authority.UNRELIABLE, 4);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));
            when(reinforcementPolicy.calculateRankBoost(any())).thenReturn(50);
            when(reinforcementPolicy.shouldUpgradeAuthority(any())).thenReturn(false);

            enabledEngine.reinforce("a1");

            verify(trustPipeline, never()).evaluate(any(), any());
        }
    }

    private static PropositionNode anchorNode(String id, int rank, Authority authority, int reinforcementCount) {
        var node = new PropositionNode("text", 0.9);
        node.setId(id);
        node.setContextId(CONTEXT_ID);
        node.setText("anchor text " + id);
        node.setRank(rank);
        node.setAuthority(authority.name());
        node.setReinforcementCount(reinforcementCount);
        return node;
    }

    private static DiceAnchorsProperties properties(boolean lifecycleEventsEnabled) {
        var anchorConfig = new DiceAnchorsProperties.AnchorConfig(
                20,
                500,
                100,
                900,
                true,
                0.65,
                "FAST_THEN_LLM",
                "TIERED",
                lifecycleEventsEnabled,
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
