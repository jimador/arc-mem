package dev.arcmem.core.memory.engine;
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

import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionStatus;
import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.core.memory.event.MemoryUnitLifecycleEvent;
import dev.arcmem.core.memory.event.ArchiveReason;
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
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArcMemEngine lifecycle event publishing")
class ArcMemEngineLifecycleEventsTest {

    private static final String CONTEXT_ID = "ctx-1";

    @Mock
    private MemoryUnitRepository repository;

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

    private ArcMemEngine enabledEngine;
    private ArcMemEngine disabledEngine;

    @BeforeEach
    void setUp() {
        lenient().when(invariantEvaluator.evaluate(any(), any(), any(), any()))
                .thenReturn(new InvariantEvaluation(List.of(), 0));

        enabledEngine = ArcMemEngineTestFactory.create(
                repository,
                properties(true),
                conflictDetector,
                conflictResolver,
                reinforcementPolicy,
                decayPolicy,
                eventPublisher,
                trustPipeline,
                canonizationGate,
                invariantEvaluator,
                new CountBasedBudgetStrategy());

        disabledEngine = ArcMemEngineTestFactory.create(
                repository,
                properties(false),
                conflictDetector,
                conflictResolver,
                reinforcementPolicy,
                decayPolicy,
                eventPublisher,
                trustPipeline,
                canonizationGate,
                invariantEvaluator,
                new CountBasedBudgetStrategy());
    }

    @Test
    @DisplayName("promote publishes MemoryUnitPromotedEvent")
    void promotePublishesPromotedEvent() {
        var proposition = org.mockito.Mockito.mock(Proposition.class);
        when(proposition.getContextIdValue()).thenReturn(CONTEXT_ID);
        when(repository.findById("p1")).thenReturn(proposition);

        enabledEngine.promote("p1", 500);

        var captor = ArgumentCaptor.forClass(MemoryUnitLifecycleEvent.Promoted.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getContextId()).isEqualTo(CONTEXT_ID);
        assertThat(captor.getValue().getUnitId()).isEqualTo("p1");
        assertThat(captor.getValue().getInitialRank()).isEqualTo(500);
    }

    @Test
    @DisplayName("reinforce publishes MemoryUnitReinforcedEvent")
    void reinforcePublishesReinforcedEvent() {
        var node = unitNode("a1", 500, Authority.PROVISIONAL, 3);
        when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));
        when(reinforcementPolicy.calculateRankBoost(any())).thenReturn(50);
        when(reinforcementPolicy.shouldUpgradeAuthority(any())).thenReturn(false);

        enabledEngine.reinforce("a1");

        var captor = ArgumentCaptor.forClass(MemoryUnitLifecycleEvent.Reinforced.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getUnitId()).isEqualTo("a1");
        assertThat(captor.getValue().getPreviousRank()).isEqualTo(500);
        assertThat(captor.getValue().getNewRank()).isEqualTo(550);
        assertThat(captor.getValue().getReinforcementCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("reinforce publishes AuthorityChanged event when threshold met")
    void reinforcePublishesAuthorityChangedEventWhenThresholdMet() {
        var node = unitNode("a1", 500, Authority.PROVISIONAL, 4);
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

        verify(eventPublisher).publishEvent(any(MemoryUnitLifecycleEvent.AuthorityChanged.class));
        verify(eventPublisher).publishEvent(any(MemoryUnitLifecycleEvent.Reinforced.class));
    }

    @Test
    @DisplayName("detectConflicts publishes ConflictDetectedEvent when conflicts exist")
    void detectConflictsPublishesEventWhenConflictsExist() {
        when(repository.findActiveUnits(CONTEXT_ID)).thenReturn(List.of(unitNode("a1", 500, Authority.RELIABLE, 0)));
        var conflict = new ConflictDetector.Conflict(
                MemoryUnit.withoutTrust("a1", "existing", 500, Authority.RELIABLE, false, 0.9, 0),
                "incoming",
                0.9,
                "negation");
        when(conflictDetector.detect(eq("incoming"), any())).thenReturn(List.of(conflict));

        enabledEngine.detectConflicts(CONTEXT_ID, "incoming");

        var captor = ArgumentCaptor.forClass(MemoryUnitLifecycleEvent.ConflictDetected.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getConflictCount()).isEqualTo(1);
        assertThat(captor.getValue().getConflictingUnitIds()).containsExactly("a1");
    }

    @Test
    @DisplayName("detectConflicts does not publish when no conflicts")
    void detectConflictsDoesNotPublishWhenNoConflicts() {
        when(repository.findActiveUnits(CONTEXT_ID)).thenReturn(List.of(unitNode("a1", 500, Authority.RELIABLE, 0)));
        when(conflictDetector.detect(eq("incoming"), any())).thenReturn(List.of());

        enabledEngine.detectConflicts(CONTEXT_ID, "incoming");

        verify(eventPublisher, never()).publishEvent(any(MemoryUnitLifecycleEvent.ConflictDetected.class));
    }

    @Test
    @DisplayName("resolveConflict publishes ConflictResolvedEvent")
    void resolveConflictPublishesEvent() {
        var conflict = new ConflictDetector.Conflict(
                MemoryUnit.withoutTrust("a1", "existing", 500, Authority.RELIABLE, false, 0.9, 0),
                "incoming",
                0.9,
                "negation");
        when(conflictResolver.resolve(eq(conflict), any())).thenReturn(ConflictResolver.Resolution.COEXIST);
        when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(unitNode("a1", 500, Authority.RELIABLE, 0)));

        enabledEngine.resolveConflict(conflict);

        var captor = ArgumentCaptor.forClass(MemoryUnitLifecycleEvent.ConflictResolved.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getExistingUnitId()).isEqualTo("a1");
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
    @DisplayName("archive publishes MemoryUnitArchivedEvent with reason")
    void archivePublishesArchivedEventWithReason() {
        when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(unitNode("a1", 500, Authority.RELIABLE, 0)));

        enabledEngine.archive("a1", ArchiveReason.DORMANCY_DECAY);

        verify(repository).archiveUnit("a1", null);
        verify(eventPublisher).publishEvent(any(MemoryUnitLifecycleEvent.Archived.class));
    }

    @Test
    @DisplayName("promote within budget publishes no Evicted event")
    void promoteWithinBudgetNoEvictedEvent() {
        var proposition = org.mockito.Mockito.mock(com.embabel.dice.proposition.Proposition.class);
        when(proposition.getContextIdValue()).thenReturn(CONTEXT_ID);
        when(repository.findById("p1")).thenReturn(proposition);
        // Active count within budget — findActiveUnits returns empty list, no eviction needed
        when(repository.findActiveUnits(CONTEXT_ID)).thenReturn(List.of());

        enabledEngine.promote("p1", 500);

        verify(eventPublisher, never()).publishEvent(any(MemoryUnitLifecycleEvent.Evicted.class));
    }

    @Test
    @DisplayName("promote over budget publishes Evicted event with correct contextUnitId and previousActivationScore")
    void promoteOverBudgetPublishesEvictedEvent() {
        var proposition = org.mockito.Mockito.mock(com.embabel.dice.proposition.Proposition.class);
        when(proposition.getContextIdValue()).thenReturn(CONTEXT_ID);
        when(repository.findById("p1")).thenReturn(proposition);
        // 21 units over the budget of 20; lowest-rank non-pinned unit (rank=150) is the eviction target
        var victim = unitNode("ev-1", 150, Authority.PROVISIONAL, 0);
        var overBudgetUnits = new java.util.ArrayList<PropositionNode>();
        overBudgetUnits.add(victim);
        for (int i = 0; i < 20; i++) {
            overBudgetUnits.add(unitNode("a" + i, 300 + i, Authority.PROVISIONAL, 0));
        }
        when(repository.findActiveUnits(CONTEXT_ID)).thenReturn(overBudgetUnits);

        enabledEngine.promote("p1", 500);

        var captor = ArgumentCaptor.forClass(MemoryUnitLifecycleEvent.Evicted.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getUnitId()).isEqualTo("ev-1");
        assertThat(captor.getValue().getPreviousRank()).isEqualTo(150);
    }

    @Test
    @DisplayName("Promoted event fires before Evicted event")
    void promotedEventFiresBeforeEvictedEvent() {
        var proposition = org.mockito.Mockito.mock(com.embabel.dice.proposition.Proposition.class);
        when(proposition.getContextIdValue()).thenReturn(CONTEXT_ID);
        when(repository.findById("p1")).thenReturn(proposition);
        // 21 units over the budget of 20; lowest-rank non-pinned unit (rank=150) is the eviction target
        var victim = unitNode("ev-1", 150, Authority.PROVISIONAL, 0);
        var overBudgetUnits = new java.util.ArrayList<PropositionNode>();
        overBudgetUnits.add(victim);
        for (int i = 0; i < 20; i++) {
            overBudgetUnits.add(unitNode("a" + i, 300 + i, Authority.PROVISIONAL, 0));
        }
        when(repository.findActiveUnits(CONTEXT_ID)).thenReturn(overBudgetUnits);

        var publishedEvents = new java.util.ArrayList<MemoryUnitLifecycleEvent>();
        org.mockito.Mockito.doAnswer(inv -> {
            publishedEvents.add(inv.getArgument(0));
            return null;
        }).when(eventPublisher).publishEvent(any(MemoryUnitLifecycleEvent.class));

        enabledEngine.promote("p1", 500);

        assertThat(publishedEvents).hasSize(2);
        assertThat(publishedEvents.get(0)).isInstanceOf(MemoryUnitLifecycleEvent.Promoted.class);
        assertThat(publishedEvents.get(1)).isInstanceOf(MemoryUnitLifecycleEvent.Evicted.class);
    }

    @Nested
    @DisplayName("Trust re-evaluation triggers")
    class TrustReEvaluationTriggers {

        @Test
        @DisplayName("reinforcement milestone triggers trust re-evaluation")
        void reinforcementMilestoneTriggersTrustReEvaluation() {
            // UNRELIABLE threshold is 7 — stub node with reinforcementCount=7 (post-increment state)
            var node = unitNode("a1", 500, Authority.UNRELIABLE, 7);
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
            var node = unitNode("a1", 500, Authority.UNRELIABLE, 4);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));
            when(reinforcementPolicy.calculateRankBoost(any())).thenReturn(50);
            when(reinforcementPolicy.shouldUpgradeAuthority(any())).thenReturn(false);

            enabledEngine.reinforce("a1");

            verify(trustPipeline, never()).evaluate(any(), any());
        }
    }

    @Nested
    @DisplayName("Trust audit records")
    class TrustAuditRecords {

        @Test
        @DisplayName("reinforce with authority upgrade produces audit record with trigger 'reinforcement'")
        void reinforceProducesAuditRecordWithReinforcementTrigger() {
            var node = unitNode("a1", 500, Authority.UNRELIABLE, 7);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));
            when(reinforcementPolicy.calculateRankBoost(any())).thenReturn(50);
            when(reinforcementPolicy.shouldUpgradeAuthority(any())).thenReturn(true);
            var trustScore = new TrustScore(0.9, Authority.RELIABLE,
                    PromotionZone.AUTO_PROMOTE, Map.of("signal1", 0.8), Instant.now());
            when(trustPipeline.evaluate(any(), any())).thenReturn(trustScore);

            enabledEngine.reinforce("a1");

            var records = enabledEngine.trustAuditLog(CONTEXT_ID);
            assertThat(records).hasSize(1);
            assertThat(records.getFirst().unitId()).isEqualTo("a1");
            assertThat(records.getFirst().contextId()).isEqualTo(CONTEXT_ID);
            assertThat(records.getFirst().triggerReason()).isEqualTo("reinforcement");
            assertThat(records.getFirst().newTrustScore()).isEqualTo(0.9);
        }

        @Test
        @DisplayName("reEvaluateTrust produces audit record with trigger 'explicit'")
        void reEvaluateTrustProducesAuditRecordWithExplicitTrigger() {
            var node = unitNode("a2", 600, Authority.RELIABLE, 5);
            when(repository.findPropositionNodeById("a2")).thenReturn(Optional.of(node));
            var trustScore = new TrustScore(0.85, Authority.RELIABLE,
                    PromotionZone.AUTO_PROMOTE, Map.of("signal1", 0.7), Instant.now());
            when(trustPipeline.evaluate(any(), any())).thenReturn(trustScore);

            enabledEngine.reEvaluateTrust("a2");

            var records = enabledEngine.trustAuditLog(CONTEXT_ID);
            assertThat(records).hasSize(1);
            assertThat(records.getFirst().triggerReason()).isEqualTo("explicit");
            assertThat(records.getFirst().priorAuthority()).isEqualTo(Authority.RELIABLE);
        }

        @Test
        @DisplayName("trust audit log filters by contextId")
        void trustAuditLogFiltersByContextId() {
            var node1 = unitNode("a1", 500, Authority.UNRELIABLE, 7);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node1));
            var trustScore = new TrustScore(0.9, Authority.RELIABLE,
                    PromotionZone.AUTO_PROMOTE, Map.of(), Instant.now());
            when(trustPipeline.evaluate(any(), any())).thenReturn(trustScore);

            enabledEngine.reEvaluateTrust("a1");

            assertThat(enabledEngine.trustAuditLog(CONTEXT_ID)).hasSize(1);
            assertThat(enabledEngine.trustAuditLog("other-ctx")).isEmpty();
            assertThat(enabledEngine.trustAuditLog(null)).hasSize(1);
        }

        @Test
        @DisplayName("clearTrustAuditLog removes entries for specified context only")
        void clearTrustAuditLogRemovesEntriesForContext() {
            var node = unitNode("a1", 500, Authority.UNRELIABLE, 7);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));
            var trustScore = new TrustScore(0.9, Authority.RELIABLE,
                    PromotionZone.AUTO_PROMOTE, Map.of(), Instant.now());
            when(trustPipeline.evaluate(any(), any())).thenReturn(trustScore);

            enabledEngine.reEvaluateTrust("a1");
            assertThat(enabledEngine.trustAuditLog(CONTEXT_ID)).hasSize(1);

            enabledEngine.clearTrustAuditLog(CONTEXT_ID);
            assertThat(enabledEngine.trustAuditLog(CONTEXT_ID)).isEmpty();
        }
    }

    private static PropositionNode unitNode(String id, int rank, Authority authority, int reinforcementCount) {
        var node = new PropositionNode(UUID.randomUUID().toString(), "test-context", "text", 0.9, 0.0, null, List.of(),
                Instant.now(), Instant.now(), PropositionStatus.ACTIVE, null, List.of());
        node.setId(id);
        node.setContextId(CONTEXT_ID);
        node.setText("unit text " + id);
        node.setRank(rank);
        node.setAuthority(authority.name());
        node.setReinforcementCount(reinforcementCount);
        return node;
    }

    private static ArcMemProperties properties(boolean lifecycleEventsEnabled) {
        var unitConfig = new ArcMemProperties.UnitConfig(
                20,
                500,
                100,
                900,
                true,
                0.65,
                DedupStrategy.FAST_THEN_LLM,
                CompliancePolicyMode.TIERED,
                lifecycleEventsEnabled,
                true,
                true,
                0.6,
                400,
                200,
                null, null, null, null, null);
        return new ArcMemProperties(
                unitConfig,
                new ArcMemProperties.MemoryConfig(true, null, null, "text-embedding-3-small", 20, 5, 2),
                new ArcMemProperties.PersistenceConfig(false),
                new ArcMemProperties.ConflictDetectionConfig(ConflictStrategy.LLM, "gpt-4o-nano"),
                new ArcMemProperties.AssemblyConfig(0, false, dev.arcmem.core.assembly.compliance.EnforcementStrategy.PROMPT_ONLY),
                null, null, null, null, null, null, null, new ArcMemProperties.LlmCallConfig(30, 10));
    }
}
