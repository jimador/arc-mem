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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArcMemEngine.demote()")
class ArcMemEngineDemoteTest {

    private static final String CONTEXT_ID = "ctx-demote";

    @Mock private MemoryUnitRepository repository;
    @Mock private ConflictDetector conflictDetector;
    @Mock private ConflictResolver conflictResolver;
    @Mock private ReinforcementPolicy reinforcementPolicy;
    @Mock private DecayPolicy decayPolicy;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TrustPipeline trustPipeline;
    @Mock private CanonizationGate canonizationGate;
    @Mock private InvariantEvaluator invariantEvaluator;

    private ArcMemEngine engine;
    private ArcMemEngine engineWithGateDisabled;

    @BeforeEach
    void setUp() {
        lenient().when(invariantEvaluator.evaluate(any(), any(), any(), any()))
                .thenReturn(new InvariantEvaluation(java.util.List.of(), 0));

        engine = ArcMemEngineTestFactory.create(
                repository,
                properties(true, true),
                conflictDetector,
                conflictResolver,
                reinforcementPolicy,
                decayPolicy,
                eventPublisher,
                trustPipeline,
                canonizationGate,
                invariantEvaluator,
                new CountBasedBudgetStrategy());

        engineWithGateDisabled = ArcMemEngineTestFactory.create(
                repository,
                properties(true, false),
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

    @Nested
    @DisplayName("demote RELIABLE memory unit")
    class DemoteReliable {

        @Test
        @DisplayName("demotes RELIABLE to UNRELIABLE and publishes AuthorityChanged DEMOTED event")
        void demoteReliableToUnreliable() {
            var node = unitNode("a1", Authority.RELIABLE);
            when(repository.findPropositionNodeById("a1")).thenReturn(Optional.of(node));

            engine.demote("a1", DemotionReason.MANUAL);

            verify(repository).setAuthority("a1", Authority.UNRELIABLE.name());
            var captor = ArgumentCaptor.forClass(MemoryUnitLifecycleEvent.AuthorityChanged.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().getUnitId()).isEqualTo("a1");
            assertThat(captor.getValue().getPreviousAuthority()).isEqualTo(Authority.RELIABLE);
            assertThat(captor.getValue().getNewAuthority()).isEqualTo(Authority.UNRELIABLE);
            assertThat(captor.getValue().getDirection()).isEqualTo(AuthorityChangeDirection.DEMOTED);
        }
    }

    @Nested
    @DisplayName("demote PROVISIONAL memory unit (falls through to archive)")
    class DemoteProvisional {

        @Test
        @DisplayName("archives PROVISIONAL memory unit instead of demoting further")
        void provisionalUnitArchived() {
            var node = unitNode("a2", Authority.PROVISIONAL);
            // findPropositionNodeById called twice: once by demote(), once by archive()
            when(repository.findPropositionNodeById("a2")).thenReturn(Optional.of(node));

            engine.demote("a2", DemotionReason.MANUAL);

            verify(repository).archiveUnit("a2", null);
            verify(eventPublisher).publishEvent(any(MemoryUnitLifecycleEvent.Archived.class));
            verify(repository, never()).setAuthority(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("demote non-existent memory unit")
    class DemoteNonExistent {

        @Test
        @DisplayName("logs warning and does nothing when memory unit not found")
        void nonExistentUnitNoException() {
            when(repository.findPropositionNodeById("missing")).thenReturn(Optional.empty());

            // Must not throw
            engine.demote("missing", DemotionReason.MANUAL);

            verify(repository, never()).setAuthority(anyString(), anyString());
            verify(repository, never()).archiveUnit(anyString(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("demote CANON memory unit with gate enabled")
    class DemoteCanonGated {

        @Test
        @DisplayName("routes CANON demotion through canonizationGate when gate is enabled")
        void canonDemotionRoutedToGate() {
            var node = unitNode("a3", Authority.CANON);
            when(repository.findPropositionNodeById("a3")).thenReturn(Optional.of(node));

            engine.demote("a3", DemotionReason.MANUAL);

            verify(canonizationGate).requestDecanonization(
                    "a3", CONTEXT_ID, node.getText(), DemotionReason.MANUAL.name(), "system");
            verify(repository, never()).setAuthority(anyString(), anyString());
            verify(repository, never()).archiveUnit(anyString(), any());
        }

        @Test
        @DisplayName("demotes CANON directly when gate is disabled")
        void canonDemotedDirectlyWhenGateDisabled() {
            var node = unitNode("a4", Authority.CANON);
            when(repository.findPropositionNodeById("a4")).thenReturn(Optional.of(node));

            engineWithGateDisabled.demote("a4", DemotionReason.MANUAL);

            verify(repository).setAuthority("a4", Authority.RELIABLE.name());
            verify(canonizationGate, never()).requestDecanonization(any(), any(), any(), any(), any());
        }
    }

    private PropositionNode unitNode(String id, Authority authority) {
        var node = new PropositionNode(UUID.randomUUID().toString(), "test-context", "unit text " + id, 0.9, 0.0, null, List.of(),
                Instant.now(), Instant.now(), PropositionStatus.ACTIVE, null, List.of());
        node.setId(id);
        node.setContextId(CONTEXT_ID);
        node.setText("unit text " + id);
        node.setRank(500);
        node.setAuthority(authority.name());
        return node;
    }

    private static ArcMemProperties properties(boolean lifecycleEventsEnabled, boolean canonizationGateEnabled) {
        var unitConfig = new ArcMemProperties.UnitConfig(
                20, 500, 100, 900, true, 0.65,
                DedupStrategy.FAST_THEN_LLM, CompliancePolicyMode.TIERED,
                lifecycleEventsEnabled,
                canonizationGateEnabled,
                true,
                0.6, 400, 200, null, null, null, null, null);
        return new ArcMemProperties(
                unitConfig,
                new ArcMemProperties.MemoryConfig(true, null, null, "text-embedding-3-small", 20, 5, 2),
                new ArcMemProperties.PersistenceConfig(false),
                new ArcMemProperties.ConflictDetectionConfig(ConflictStrategy.LLM, "gpt-4o-nano"),
                new ArcMemProperties.AssemblyConfig(0, false, EnforcementStrategy.PROMPT_ONLY),
                null, null, null, null, null, null, new ArcMemProperties.LlmCallConfig(30, 10));
    }
}
