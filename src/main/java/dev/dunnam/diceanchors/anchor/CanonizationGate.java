package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.event.AnchorLifecycleEvent;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Human-in-the-loop (HITL) approval gate for all CANON authority transitions.
 * <p>
 * The canonization gate prevents automatic promotion to CANON (invariant A3a) and
 * protects CANON anchors from automatic demotion (invariant A3b) by holding authority
 * transitions in a pending queue until explicitly approved or rejected.
 *
 * <h2>Request lifecycle</h2>
 * <ol>
 *   <li>Caller invokes {@link #requestCanonization} or {@link #requestDecanonization}.
 *       A {@link CanonizationRequest} with status {@link CanonizationStatus#PENDING} is created.</li>
 *   <li>If {@code auto-approve-in-simulation} is enabled and the context matches {@code sim-*},
 *       the request is immediately approved (for automated testing).</li>
 *   <li>Otherwise, the request waits in the pending queue until {@link #approve} or {@link #reject}
 *       is called.</li>
 *   <li>On approval, the authority transition is executed directly via the repository and an
 *       {@link AnchorLifecycleEvent.AuthorityChanged} event is published.</li>
 * </ol>
 *
 * <h2>Idempotency</h2>
 * If a pending request already exists for an anchor, the existing request ID is returned
 * rather than creating a duplicate.
 *
 * <h2>Stale requests</h2>
 * If the anchor's authority has changed since the request was created, {@link #approve}
 * marks the request as {@link CanonizationStatus#STALE} and does not apply the change.
 */
@Service
public class CanonizationGate {

    private static final Logger logger = LoggerFactory.getLogger(CanonizationGate.class);

    private final AnchorRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final DiceAnchorsProperties.AnchorConfig config;

    // In-memory pending requests: requestId -> CanonizationRequest
    private final ConcurrentHashMap<String, CanonizationRequest> requests = new ConcurrentHashMap<>();

    public CanonizationGate(AnchorRepository repository,
                            ApplicationEventPublisher eventPublisher,
                            DiceAnchorsProperties properties) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.config = properties.anchor();
    }

    /**
     * Request promotion of an anchor to CANON authority.
     * <p>
     * If a pending request already exists for this anchor, the existing request is returned
     * (idempotency). If auto-approve is enabled for simulation contexts, the request is
     * immediately approved.
     *
     * @param anchorId          the anchor to canonize
     * @param contextId         the context the anchor belongs to
     * @param anchorText        snapshot of anchor text at request time
     * @param currentAuthority  the anchor's current authority (pre-transition)
     * @param reason            human-readable reason for the transition
     * @param requestedBy       identifier of the requesting actor
     * @return the created (or existing) CanonizationRequest
     */
    public CanonizationRequest requestCanonization(String anchorId, String contextId,
                                                   String anchorText, Authority currentAuthority,
                                                   String reason, String requestedBy) {
        return createRequest(anchorId, contextId, anchorText, currentAuthority, Authority.CANON,
                reason, requestedBy);
    }

    /**
     * Request demotion of a CANON anchor to the next lower authority level.
     * <p>
     * If a pending request already exists for this anchor, the existing request is returned.
     *
     * @param anchorId          the anchor to decanonize
     * @param contextId         the context the anchor belongs to
     * @param anchorText        snapshot of anchor text at request time
     * @param reason            human-readable reason (e.g., DemotionReason name)
     * @param requestedBy       identifier of the requesting actor
     * @return the created (or existing) CanonizationRequest
     */
    public CanonizationRequest requestDecanonization(String anchorId, String contextId,
                                                     String anchorText, String reason,
                                                     String requestedBy) {
        return createRequest(anchorId, contextId, anchorText, Authority.CANON,
                Authority.CANON.previousLevel(), reason, requestedBy);
    }

    /**
     * Approve a pending canonization request and execute the authority transition.
     * <p>
     * Stale validation: if the anchor's current authority no longer matches the
     * {@link CanonizationRequest#currentAuthority()}, the request is marked STALE and
     * the transition is not applied.
     *
     * @param requestId the ID of the request to approve
     * @return the updated request, or empty if not found
     */
    public Optional<CanonizationRequest> approve(String requestId) {
        var request = requests.get(requestId);
        if (request == null) {
            logger.warn("Cannot approve: request {} not found", requestId);
            return Optional.empty();
        }
        if (request.status() != CanonizationStatus.PENDING) {
            logger.warn("Cannot approve: request {} is in state {}", requestId, request.status());
            return Optional.of(request);
        }

        // Stale check: verify current authority still matches request's expected state
        var nodeOpt = repository.findPropositionNodeById(request.anchorId());
        if (nodeOpt.isEmpty()) {
            logger.warn("Cannot approve: anchor {} not found", request.anchorId());
            var stale = withStatus(request, CanonizationStatus.STALE);
            requests.put(requestId, stale);
            return Optional.of(stale);
        }
        var currentAuthStr = nodeOpt.get().getAuthority();
        var currentAuth = currentAuthStr != null
                ? Authority.valueOf(currentAuthStr) : Authority.PROVISIONAL;
        if (currentAuth != request.currentAuthority()) {
            logger.warn("Stale canonization request {}: expected {} but found {}",
                    requestId, request.currentAuthority(), currentAuth);
            var stale = withStatus(request, CanonizationStatus.STALE);
            requests.put(requestId, stale);
            return Optional.of(stale);
        }

        // Execute the transition
        repository.setAuthority(request.anchorId(), request.requestedAuthority().name());
        var direction = request.requestedAuthority().level() > request.currentAuthority().level()
                ? AuthorityChangeDirection.PROMOTED : AuthorityChangeDirection.DEMOTED;
        try {
            eventPublisher.publishEvent(AnchorLifecycleEvent.authorityChanged(
                    this, request.contextId(), request.anchorId(),
                    request.currentAuthority(), request.requestedAuthority(),
                    direction, request.reason()));
        } catch (Exception e) {
            logger.warn("Failed to publish AuthorityChanged event after canonization approval: {}", e.getMessage());
        }

        var approved = withStatus(request, CanonizationStatus.APPROVED);
        requests.put(requestId, approved);
        logger.info("Canonization request {} approved: anchor {} {} -> {}",
                requestId, request.anchorId(), request.currentAuthority(), request.requestedAuthority());
        return Optional.of(approved);
    }

    /**
     * Reject a pending canonization request. The anchor's authority is unchanged.
     *
     * @param requestId the ID of the request to reject
     * @return the updated request, or empty if not found
     */
    public Optional<CanonizationRequest> reject(String requestId) {
        var request = requests.get(requestId);
        if (request == null) {
            logger.warn("Cannot reject: request {} not found", requestId);
            return Optional.empty();
        }
        if (request.status() != CanonizationStatus.PENDING) {
            logger.warn("Cannot reject: request {} is in state {}", requestId, request.status());
            return Optional.of(request);
        }
        var rejected = withStatus(request, CanonizationStatus.REJECTED);
        requests.put(requestId, rejected);
        logger.info("Canonization request {} rejected for anchor {}", requestId, request.anchorId());
        return Optional.of(rejected);
    }

    /**
     * Returns all pending canonization requests across all contexts.
     */
    public List<CanonizationRequest> pendingRequests() {
        return requests.values().stream()
                .filter(r -> r.status() == CanonizationStatus.PENDING)
                .toList();
    }

    /**
     * Returns pending canonization requests for a specific context.
     *
     * @param contextId the context to filter by
     */
    public List<CanonizationRequest> pendingRequests(String contextId) {
        return requests.values().stream()
                .filter(r -> r.status() == CanonizationStatus.PENDING
                        && contextId.equals(r.contextId()))
                .toList();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private CanonizationRequest createRequest(String anchorId, String contextId,
                                              String anchorText, Authority currentAuthority,
                                              Authority requestedAuthority,
                                              String reason, String requestedBy) {
        // Idempotency: return existing pending request for this anchor if one exists
        var existing = requests.values().stream()
                .filter(r -> r.status() == CanonizationStatus.PENDING
                        && anchorId.equals(r.anchorId())
                        && requestedAuthority == r.requestedAuthority())
                .findFirst();
        if (existing.isPresent()) {
            logger.debug("Returning existing pending canonization request {} for anchor {}",
                    existing.get().id(), anchorId);
            return existing.get();
        }

        var request = new CanonizationRequest(
                UUID.randomUUID().toString(),
                anchorId, contextId, anchorText,
                currentAuthority, requestedAuthority,
                reason, requestedBy,
                Instant.now(),
                CanonizationStatus.PENDING
        );
        requests.put(request.id(), request);
        logger.info("Canonization request {} created: anchor {} {} -> {} (requestedBy={})",
                request.id(), anchorId, currentAuthority, requestedAuthority, requestedBy);

        // Simulation auto-approve: immediately approve for sim-* contexts
        if (config.autoApproveInSimulation() && contextId != null && contextId.startsWith("sim-")) {
            logger.debug("Auto-approving canonization request {} for simulation context {}", request.id(), contextId);
            return approve(request.id()).orElse(request);
        }

        return request;
    }

    private static CanonizationRequest withStatus(CanonizationRequest r, CanonizationStatus newStatus) {
        return new CanonizationRequest(r.id(), r.anchorId(), r.contextId(), r.anchorText(),
                r.currentAuthority(), r.requestedAuthority(), r.reason(), r.requestedBy(),
                r.createdAt(), newStatus);
    }
}
