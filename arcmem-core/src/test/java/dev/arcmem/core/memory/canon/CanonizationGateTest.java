package dev.arcmem.core.memory.canon;
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
import dev.arcmem.core.persistence.CanonizationRequestRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CanonizationGate")
class CanonizationGateTest {

    private static final String CONTEXT_ID = "ctx-gate";
    private static final String UNIT_ID = "unit-1";
    private static final String UNIT_TEXT = "The sky is blue";

    @Mock private MemoryUnitRepository repository;
    @Mock private CanonizationRequestRepository canonizationRequestRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private CanonizationGate gate;

    @BeforeEach
    void setUp() {
        gate = new CanonizationGate(repository, canonizationRequestRepository, eventPublisher, properties(true, true));
    }

    @Nested
    @DisplayName("requestCanonization")
    class RequestCanonization {

        @Test
        @DisplayName("creates PENDING request")
        void createsPendingRequest() {
            // No existing pending request
            when(canonizationRequestRepository.findPendingCanonizationRequest(UNIT_ID, Authority.CANON.name()))
                    .thenReturn(Optional.empty());

            var request = gate.requestCanonization(
                    UNIT_ID, CONTEXT_ID, UNIT_TEXT, Authority.RELIABLE, "test", "user");

            assertThat(request.status()).isEqualTo(CanonizationStatus.PENDING);
            assertThat(request.unitId()).isEqualTo(UNIT_ID);
            assertThat(request.requestedAuthority()).isEqualTo(Authority.CANON);
            assertThat(request.currentAuthority()).isEqualTo(Authority.RELIABLE);
            assertThat(request.resolvedAt()).isNull();
            assertThat(request.resolvedBy()).isNull();
            assertThat(request.resolutionNote()).isNull();
        }

        @Test
        @DisplayName("idempotency: second call returns same request ID")
        void idempotentDuplicateRequest() {
            // First call: no existing request
            when(canonizationRequestRepository.findPendingCanonizationRequest(UNIT_ID, Authority.CANON.name()))
                    .thenReturn(Optional.empty());

            var first = gate.requestCanonization(
                    UNIT_ID, CONTEXT_ID, UNIT_TEXT, Authority.RELIABLE, "test", "user");

            // Second call: existing request found
            when(canonizationRequestRepository.findPendingCanonizationRequest(UNIT_ID, Authority.CANON.name()))
                    .thenReturn(Optional.of(requestMap(first.id(), UNIT_ID, CONTEXT_ID)));

            var second = gate.requestCanonization(
                    UNIT_ID, CONTEXT_ID, UNIT_TEXT, Authority.RELIABLE, "test", "user");

            assertThat(second.id()).isEqualTo(first.id());
        }
    }

    @Nested
    @DisplayName("approve")
    class Approve {

        @Test
        @DisplayName("approved pending request executes authority transition and publishes event")
        void approvePendingRequest() {
            when(canonizationRequestRepository.findPendingCanonizationRequest(UNIT_ID, Authority.CANON.name()))
                    .thenReturn(Optional.empty());

            var node = unitNode(UNIT_ID, Authority.RELIABLE);
            when(repository.findPropositionNodeById(UNIT_ID)).thenReturn(Optional.of(node));

            var request = gate.requestCanonization(
                    UNIT_ID, CONTEXT_ID, UNIT_TEXT, Authority.RELIABLE, "test", "user");

            // Mock loading the request by ID for approve
            when(canonizationRequestRepository.findCanonizationRequestById(request.id()))
                    .thenReturn(Optional.of(requestMap(request.id(), UNIT_ID, CONTEXT_ID)));

            var result = gate.approve(request.id());

            assertThat(result).isPresent();
            assertThat(result.get().status()).isEqualTo(CanonizationStatus.APPROVED);
            verify(repository).setAuthority(UNIT_ID, Authority.CANON.name());
            verify(canonizationRequestRepository).resolveCanonizationRequest(eq(request.id()), eq("APPROVED"), isNull(), isNull());
            var captor = ArgumentCaptor.forClass(MemoryUnitLifecycleEvent.AuthorityChanged.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().getDirection()).isEqualTo(AuthorityChangeDirection.PROMOTED);
        }

        @Test
        @DisplayName("stale request not applied when memory unit authority changed since request")
        void staleRequestNotApplied() {
            when(canonizationRequestRepository.findPendingCanonizationRequest(UNIT_ID, Authority.CANON.name()))
                    .thenReturn(Optional.empty());

            // Request was created when unit was RELIABLE
            var request = gate.requestCanonization(
                    UNIT_ID, CONTEXT_ID, UNIT_TEXT, Authority.RELIABLE, "test", "user");

            // Mock loading the request by ID
            when(canonizationRequestRepository.findCanonizationRequestById(request.id()))
                    .thenReturn(Optional.of(requestMap(request.id(), UNIT_ID, CONTEXT_ID)));

            // But now unit is at UNRELIABLE (changed since request)
            var node = unitNode(UNIT_ID, Authority.UNRELIABLE);
            when(repository.findPropositionNodeById(UNIT_ID)).thenReturn(Optional.of(node));

            var result = gate.approve(request.id());

            assertThat(result).isPresent();
            assertThat(result.get().status()).isEqualTo(CanonizationStatus.STALE);
            verify(repository, never()).setAuthority(any(), any());
            verify(eventPublisher, never()).publishEvent(any());
            verify(canonizationRequestRepository).resolveCanonizationRequest(eq(request.id()), eq("STALE"), isNull(), isNull());
        }
    }

    @Nested
    @DisplayName("reject")
    class Reject {

        @Test
        @DisplayName("rejected request has REJECTED status and does not call setAuthority")
        void rejectPendingRequest() {
            when(canonizationRequestRepository.findPendingCanonizationRequest(UNIT_ID, Authority.CANON.name()))
                    .thenReturn(Optional.empty());

            var request = gate.requestCanonization(
                    UNIT_ID, CONTEXT_ID, UNIT_TEXT, Authority.RELIABLE, "test", "user");

            // Mock loading the request by ID
            when(canonizationRequestRepository.findCanonizationRequestById(request.id()))
                    .thenReturn(Optional.of(requestMap(request.id(), UNIT_ID, CONTEXT_ID)));

            var result = gate.reject(request.id());

            assertThat(result).isPresent();
            assertThat(result.get().status()).isEqualTo(CanonizationStatus.REJECTED);
            verify(repository, never()).setAuthority(any(), any());
            verify(eventPublisher, never()).publishEvent(any());
            verify(canonizationRequestRepository).resolveCanonizationRequest(eq(request.id()), eq("REJECTED"), isNull(), isNull());
        }
    }

    @Nested
    @DisplayName("pendingRequests")
    class PendingRequests {

        @Test
        @DisplayName("pendingRequests by context returns results from repository")
        void pendingRequestsByContext() {
            var requestMap = requestMap("req-1", "a1", CONTEXT_ID);
            when(canonizationRequestRepository.findPendingCanonizationRequests(CONTEXT_ID))
                    .thenReturn(java.util.List.of(requestMap));

            var pending = gate.pendingRequests(CONTEXT_ID);

            assertThat(pending).hasSize(1);
            assertThat(pending.getFirst().unitId()).isEqualTo("a1");
        }
    }

    @Nested
    @DisplayName("auto-approve promotions")
    class AutoApprovePromotions {

        @Test
        @DisplayName("promotion to CANON is auto-approved when autoApprovePromotions=true")
        void promotionAutoApproved() {
            when(canonizationRequestRepository.findPendingCanonizationRequest(UNIT_ID, Authority.CANON.name()))
                    .thenReturn(Optional.empty());

            var node = unitNode(UNIT_ID, Authority.RELIABLE);
            when(repository.findPropositionNodeById(UNIT_ID)).thenReturn(Optional.of(node));

            when(canonizationRequestRepository.findCanonizationRequestById(anyString()))
                    .thenAnswer(inv -> {
                        var id = (String) inv.getArgument(0);
                        return Optional.of(requestMap(id, UNIT_ID, CONTEXT_ID));
                    });

            var request = gate.requestCanonization(
                    UNIT_ID, CONTEXT_ID, UNIT_TEXT, Authority.RELIABLE, "test", "system");

            assertThat(request.status()).isEqualTo(CanonizationStatus.APPROVED);
            verify(repository).setAuthority(UNIT_ID, Authority.CANON.name());
            verify(eventPublisher).publishEvent(any(MemoryUnitLifecycleEvent.AuthorityChanged.class));
        }

        @Test
        @DisplayName("decanonization is never auto-approved — requires HITL")
        void decanonizationNotAutoApproved() {
            when(canonizationRequestRepository.findPendingCanonizationRequest(UNIT_ID, Authority.RELIABLE.name()))
                    .thenReturn(Optional.empty());

            var request = gate.requestDecanonization(
                    UNIT_ID, CONTEXT_ID, UNIT_TEXT, "TRUST_DEGRADATION", "system");

            assertThat(request.status()).isEqualTo(CanonizationStatus.PENDING);
            verify(repository, never()).setAuthority(any(), any());
        }
    }

    @Nested
    @DisplayName("markContextRequestsStale")
    class MarkContextRequestsStale {

        @Test
        @DisplayName("delegates to repository")
        void delegatesToRepository() {
            gate.markContextRequestsStale("sim-cleanup");

            verify(canonizationRequestRepository).markContextRequestsStale("sim-cleanup");
        }
    }

    private static PropositionNode unitNode(String id, Authority authority) {
        var node = new PropositionNode(UUID.randomUUID().toString(), "test-context", "unit text", 0.9, 0.0, null, List.of(),
                Instant.now(), Instant.now(), PropositionStatus.ACTIVE, null, List.of());
        node.setId(id);
        node.setContextId(CONTEXT_ID);
        node.setText("unit text " + id);
        node.setRank(700);
        node.setAuthority(authority.name());
        return node;
    }

    private static Map<String, Object> requestMap(String id, String unitId, String contextId) {
        return Map.ofEntries(
                Map.entry("id", id),
                Map.entry("unitId", unitId),
                Map.entry("contextId", contextId),
                Map.entry("unitText", UNIT_TEXT),
                Map.entry("currentAuthority", Authority.RELIABLE.name()),
                Map.entry("requestedAuthority", Authority.CANON.name()),
                Map.entry("reason", "test"),
                Map.entry("requestedBy", "user"),
                Map.entry("status", "PENDING"),
                Map.entry("createdAt", java.time.Instant.now().toString())
        );
    }

    private static ArcMemProperties properties(boolean gateEnabled, boolean autoApprovePromotions) {
        var unitConfig = new ArcMemProperties.UnitConfig(
                20, 500, 100, 900, true, 0.65,
                DedupStrategy.FAST_THEN_LLM, CompliancePolicyMode.TIERED,
                true, gateEnabled, autoApprovePromotions,
                0.6, 400, 200, null, null, null, null, null);
        return new ArcMemProperties(
                unitConfig,
                new ArcMemProperties.MemoryConfig(true, null, null, "text-embedding-3-small", 20, 5, 2),
                new ArcMemProperties.PersistenceConfig(false),
                new ArcMemProperties.ConflictDetectionConfig(ConflictStrategy.LLM, "gpt-4o-nano"),
                new ArcMemProperties.AssemblyConfig(0, false, dev.arcmem.core.assembly.compliance.EnforcementStrategy.PROMPT_ONLY),
                null, null, null, null, null, null, null, new ArcMemProperties.LlmCallConfig(30, 10));
    }
}
