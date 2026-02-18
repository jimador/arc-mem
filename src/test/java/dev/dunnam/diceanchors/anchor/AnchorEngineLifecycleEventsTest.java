package dev.dunnam.diceanchors.anchor;

import com.embabel.dice.proposition.Proposition;
import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.event.AnchorPromotedEvent;
import dev.dunnam.diceanchors.anchor.event.AnchorReinforcedEvent;
import dev.dunnam.diceanchors.anchor.event.ArchiveReason;
import dev.dunnam.diceanchors.anchor.event.AuthorityUpgradedEvent;
import dev.dunnam.diceanchors.anchor.event.ConflictDetectedEvent;
import dev.dunnam.diceanchors.anchor.event.ConflictResolvedEvent;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.persistence.PropositionNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private ApplicationEventPublisher eventPublisher;

    private AnchorEngine enabledEngine;
    private AnchorEngine disabledEngine;

    @BeforeEach
    void setUp() {
        enabledEngine = new AnchorEngine(
                repository,
                properties(true),
                conflictDetector,
                conflictResolver,
                reinforcementPolicy,
                eventPublisher);

        disabledEngine = new AnchorEngine(
                repository,
                properties(false),
                conflictDetector,
                conflictResolver,
                reinforcementPolicy,
                eventPublisher);
    }

    @Test
    @DisplayName("promote publishes AnchorPromotedEvent")
    void promotePublishesPromotedEvent() {
        var proposition = org.mockito.Mockito.mock(Proposition.class);
        when(proposition.getContextIdValue()).thenReturn(CONTEXT_ID);
        when(repository.findById("p1")).thenReturn(proposition);

        enabledEngine.promote("p1", 500);

        var captor = ArgumentCaptor.forClass(AnchorPromotedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getContextId()).isEqualTo(CONTEXT_ID);
        assertThat(captor.getValue().getAnchorId()).isEqualTo("p1");
        assertThat(captor.getValue().getInitialRank()).isEqualTo(500);
    }

    @Test
    @DisplayName("reinforce publishes AnchorReinforcedEvent")
    void reinforcePublishesReinforcedEvent() {
        var node = anchorNode("a1", 500, Authority.PROVISIONAL, 3);
        when(repository.findPropositionNodeById("a1")).thenReturn(node);
        when(reinforcementPolicy.calculateRankBoost(any())).thenReturn(50);
        when(reinforcementPolicy.shouldUpgradeAuthority(any())).thenReturn(false);

        enabledEngine.reinforce("a1");

        var captor = ArgumentCaptor.forClass(AnchorReinforcedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getAnchorId()).isEqualTo("a1");
        assertThat(captor.getValue().getPreviousRank()).isEqualTo(500);
        assertThat(captor.getValue().getNewRank()).isEqualTo(550);
        assertThat(captor.getValue().getReinforcementCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("reinforce publishes AuthorityUpgradedEvent when threshold met")
    void reinforcePublishesAuthorityUpgradedEventWhenThresholdMet() {
        var node = anchorNode("a1", 500, Authority.PROVISIONAL, 4);
        when(repository.findPropositionNodeById("a1")).thenReturn(node);
        when(reinforcementPolicy.calculateRankBoost(any())).thenReturn(25);
        when(reinforcementPolicy.shouldUpgradeAuthority(any())).thenReturn(true);

        enabledEngine.reinforce("a1");

        verify(eventPublisher).publishEvent(any(AuthorityUpgradedEvent.class));
        verify(eventPublisher).publishEvent(any(AnchorReinforcedEvent.class));
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

        var captor = ArgumentCaptor.forClass(ConflictDetectedEvent.class);
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

        verify(eventPublisher, never()).publishEvent(any(ConflictDetectedEvent.class));
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
        when(repository.findPropositionNodeById("a1")).thenReturn(anchorNode("a1", 500, Authority.RELIABLE, 0));

        enabledEngine.resolveConflict(conflict);

        var captor = ArgumentCaptor.forClass(ConflictResolvedEvent.class);
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
        when(repository.findPropositionNodeById("a1")).thenReturn(anchorNode("a1", 500, Authority.RELIABLE, 0));

        enabledEngine.archive("a1", ArchiveReason.DORMANCY_DECAY);

        verify(repository).archiveAnchor("a1");
        verify(eventPublisher).publishEvent(any(dev.dunnam.diceanchors.anchor.event.AnchorArchivedEvent.class));
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
                lifecycleEventsEnabled);
        return new DiceAnchorsProperties(
                anchorConfig,
                new DiceAnchorsProperties.ChatConfig("dm", 200, null),
                new DiceAnchorsProperties.MemoryConfig(true, null, null, "text-embedding-3-small", 20, 5, 2),
                new DiceAnchorsProperties.PersistenceConfig(false),
                new DiceAnchorsProperties.SimConfig("gpt-4.1-mini", 30),
                new DiceAnchorsProperties.ConflictDetectionConfig("llm", "gpt-4o-nano"),
                new DiceAnchorsProperties.RunHistoryConfig("memory"),
                new DiceAnchorsProperties.AssemblyConfig(0));
    }
}
