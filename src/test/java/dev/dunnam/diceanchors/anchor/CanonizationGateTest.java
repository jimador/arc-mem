package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.event.AnchorLifecycleEvent;
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

import java.util.Map;
import java.util.Optional;

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
    private static final String ANCHOR_ID = "anchor-1";
    private static final String ANCHOR_TEXT = "The sky is blue";

    @Mock private AnchorRepository repository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private CanonizationGate gate;

    @BeforeEach
    void setUp() {
        gate = new CanonizationGate(repository, eventPublisher, properties(true, true));
    }

    @Nested
    @DisplayName("requestCanonization")
    class RequestCanonization {

        @Test
        @DisplayName("creates PENDING request")
        void createsPendingRequest() {
            // No existing pending request
            when(repository.findPendingCanonizationRequest(ANCHOR_ID, Authority.CANON.name()))
                    .thenReturn(Optional.empty());

            var request = gate.requestCanonization(
                    ANCHOR_ID, CONTEXT_ID, ANCHOR_TEXT, Authority.RELIABLE, "test", "user");

            assertThat(request.status()).isEqualTo(CanonizationStatus.PENDING);
            assertThat(request.anchorId()).isEqualTo(ANCHOR_ID);
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
            when(repository.findPendingCanonizationRequest(ANCHOR_ID, Authority.CANON.name()))
                    .thenReturn(Optional.empty());

            var first = gate.requestCanonization(
                    ANCHOR_ID, CONTEXT_ID, ANCHOR_TEXT, Authority.RELIABLE, "test", "user");

            // Second call: existing request found
            when(repository.findPendingCanonizationRequest(ANCHOR_ID, Authority.CANON.name()))
                    .thenReturn(Optional.of(requestMap(first.id(), ANCHOR_ID, CONTEXT_ID)));

            var second = gate.requestCanonization(
                    ANCHOR_ID, CONTEXT_ID, ANCHOR_TEXT, Authority.RELIABLE, "test", "user");

            assertThat(second.id()).isEqualTo(first.id());
        }
    }

    @Nested
    @DisplayName("approve")
    class Approve {

        @Test
        @DisplayName("approved pending request executes authority transition and publishes event")
        void approvePendingRequest() {
            when(repository.findPendingCanonizationRequest(ANCHOR_ID, Authority.CANON.name()))
                    .thenReturn(Optional.empty());

            var node = anchorNode(ANCHOR_ID, Authority.RELIABLE);
            when(repository.findPropositionNodeById(ANCHOR_ID)).thenReturn(Optional.of(node));

            var request = gate.requestCanonization(
                    ANCHOR_ID, CONTEXT_ID, ANCHOR_TEXT, Authority.RELIABLE, "test", "user");

            // Mock loading the request by ID for approve
            when(repository.findCanonizationRequestById(request.id()))
                    .thenReturn(Optional.of(requestMap(request.id(), ANCHOR_ID, CONTEXT_ID)));

            var result = gate.approve(request.id());

            assertThat(result).isPresent();
            assertThat(result.get().status()).isEqualTo(CanonizationStatus.APPROVED);
            verify(repository).setAuthority(ANCHOR_ID, Authority.CANON.name());
            verify(repository).resolveCanonizationRequest(eq(request.id()), eq("APPROVED"), isNull(), isNull());
            var captor = ArgumentCaptor.forClass(AnchorLifecycleEvent.AuthorityChanged.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().getDirection()).isEqualTo(AuthorityChangeDirection.PROMOTED);
        }

        @Test
        @DisplayName("stale request not applied when anchor authority changed since request")
        void staleRequestNotApplied() {
            when(repository.findPendingCanonizationRequest(ANCHOR_ID, Authority.CANON.name()))
                    .thenReturn(Optional.empty());

            // Request was created when anchor was RELIABLE
            var request = gate.requestCanonization(
                    ANCHOR_ID, CONTEXT_ID, ANCHOR_TEXT, Authority.RELIABLE, "test", "user");

            // Mock loading the request by ID
            when(repository.findCanonizationRequestById(request.id()))
                    .thenReturn(Optional.of(requestMap(request.id(), ANCHOR_ID, CONTEXT_ID)));

            // But now anchor is at UNRELIABLE (changed since request)
            var node = anchorNode(ANCHOR_ID, Authority.UNRELIABLE);
            when(repository.findPropositionNodeById(ANCHOR_ID)).thenReturn(Optional.of(node));

            var result = gate.approve(request.id());

            assertThat(result).isPresent();
            assertThat(result.get().status()).isEqualTo(CanonizationStatus.STALE);
            verify(repository, never()).setAuthority(any(), any());
            verify(eventPublisher, never()).publishEvent(any());
            verify(repository).resolveCanonizationRequest(eq(request.id()), eq("STALE"), isNull(), isNull());
        }
    }

    @Nested
    @DisplayName("reject")
    class Reject {

        @Test
        @DisplayName("rejected request has REJECTED status and does not call setAuthority")
        void rejectPendingRequest() {
            when(repository.findPendingCanonizationRequest(ANCHOR_ID, Authority.CANON.name()))
                    .thenReturn(Optional.empty());

            var request = gate.requestCanonization(
                    ANCHOR_ID, CONTEXT_ID, ANCHOR_TEXT, Authority.RELIABLE, "test", "user");

            // Mock loading the request by ID
            when(repository.findCanonizationRequestById(request.id()))
                    .thenReturn(Optional.of(requestMap(request.id(), ANCHOR_ID, CONTEXT_ID)));

            var result = gate.reject(request.id());

            assertThat(result).isPresent();
            assertThat(result.get().status()).isEqualTo(CanonizationStatus.REJECTED);
            verify(repository, never()).setAuthority(any(), any());
            verify(eventPublisher, never()).publishEvent(any());
            verify(repository).resolveCanonizationRequest(eq(request.id()), eq("REJECTED"), isNull(), isNull());
        }
    }

    @Nested
    @DisplayName("pendingRequests")
    class PendingRequests {

        @Test
        @DisplayName("pendingRequests by context returns results from repository")
        void pendingRequestsByContext() {
            var requestMap = requestMap("req-1", "a1", CONTEXT_ID);
            when(repository.findPendingCanonizationRequests(CONTEXT_ID))
                    .thenReturn(java.util.List.of(requestMap));

            var pending = gate.pendingRequests(CONTEXT_ID);

            assertThat(pending).hasSize(1);
            assertThat(pending.getFirst().anchorId()).isEqualTo("a1");
        }
    }

    @Nested
    @DisplayName("simulation auto-approve")
    class SimulationAutoApprove {

        @Test
        @DisplayName("sim-* context with autoApproveInSimulation=true immediately approves request")
        void simContextAutoApproved() {
            var simContextId = "sim-abc123";
            when(repository.findPendingCanonizationRequest(ANCHOR_ID, Authority.CANON.name()))
                    .thenReturn(Optional.empty());

            var node = anchorNode(ANCHOR_ID, Authority.RELIABLE);
            when(repository.findPropositionNodeById(ANCHOR_ID)).thenReturn(Optional.of(node));

            // The auto-approve path calls approve() which loads the request by ID
            when(repository.findCanonizationRequestById(anyString()))
                    .thenAnswer(inv -> {
                        var id = (String) inv.getArgument(0);
                        return Optional.of(requestMap(id, ANCHOR_ID, simContextId));
                    });

            var request = gate.requestCanonization(
                    ANCHOR_ID, simContextId, ANCHOR_TEXT, Authority.RELIABLE, "test", "system");

            // Auto-approve should have fired immediately
            assertThat(request.status()).isEqualTo(CanonizationStatus.APPROVED);
            verify(repository).setAuthority(ANCHOR_ID, Authority.CANON.name());
            verify(eventPublisher).publishEvent(any(AnchorLifecycleEvent.AuthorityChanged.class));
        }

        @Test
        @DisplayName("non-sim context does not auto-approve")
        void nonSimContextNotAutoApproved() {
            when(repository.findPendingCanonizationRequest(ANCHOR_ID, Authority.CANON.name()))
                    .thenReturn(Optional.empty());

            var request = gate.requestCanonization(
                    ANCHOR_ID, CONTEXT_ID, ANCHOR_TEXT, Authority.RELIABLE, "test", "user");

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

            verify(repository).markContextRequestsStale("sim-cleanup");
        }
    }

    private static PropositionNode anchorNode(String id, Authority authority) {
        var node = new PropositionNode("anchor text", 0.9);
        node.setId(id);
        node.setContextId(CONTEXT_ID);
        node.setText("anchor text " + id);
        node.setRank(700);
        node.setAuthority(authority.name());
        return node;
    }

    private static Map<String, Object> requestMap(String id, String anchorId, String contextId) {
        return Map.ofEntries(
                Map.entry("id", id),
                Map.entry("anchorId", anchorId),
                Map.entry("contextId", contextId),
                Map.entry("anchorText", ANCHOR_TEXT),
                Map.entry("currentAuthority", Authority.RELIABLE.name()),
                Map.entry("requestedAuthority", Authority.CANON.name()),
                Map.entry("reason", "test"),
                Map.entry("requestedBy", "user"),
                Map.entry("status", "PENDING"),
                Map.entry("createdAt", java.time.Instant.now().toString())
        );
    }

    private static DiceAnchorsProperties properties(boolean gateEnabled, boolean autoApproveInSim) {
        var anchorConfig = new DiceAnchorsProperties.AnchorConfig(
                20, 500, 100, 900, true, 0.65,
                "FAST_THEN_LLM", "TIERED",
                true, gateEnabled, autoApproveInSim,
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
