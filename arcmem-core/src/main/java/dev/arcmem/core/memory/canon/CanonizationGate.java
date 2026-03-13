package dev.arcmem.core.memory.canon;

import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.core.memory.conflict.AuthorityChangeDirection;
import dev.arcmem.core.memory.event.MemoryUnitLifecycleEvent;
import dev.arcmem.core.memory.model.Authority;
import dev.arcmem.core.persistence.CanonizationRequestRepository;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Human-in-the-loop (HITL) approval gate for all CANON authority transitions.
 * <p>
 * The canonization gate prevents automatic promotion to CANON (invariant A3a) and
 * protects CANON units from automatic demotion (invariant A3b) by holding authority
 * transitions in a pending queue until explicitly approved or rejected.
 *
 * <h2>Request lifecycle</h2>
 * <ol>
 *   <li>Caller invokes {@link #requestCanonization} or {@link #requestDecanonization}.
 *       A {@link CanonizationRequest} with status {@link CanonizationStatus#PENDING} is created.</li>
 *   <li>If {@code auto-approve-promotions} is enabled and the request is a promotion to CANON,
 *       the request is immediately approved. Decanonization always requires HITL approval.</li>
 *   <li>Otherwise, the request waits in the pending queue until {@link #approve} or {@link #reject}
 *       is called.</li>
 *   <li>On approval, the authority transition is executed directly via the repository and an
 *       {@link MemoryUnitLifecycleEvent.AuthorityChanged} event is published.</li>
 * </ol>
 *
 * <h2>Idempotency</h2>
 * If a pending request already exists for an unit, the existing request ID is returned
 * rather than creating a duplicate.
 *
 * <h2>Stale requests</h2>
 * If the unit's authority has changed since the request was created, {@link #approve}
 * marks the request as {@link CanonizationStatus#STALE} and does not apply the change.
 *
 * <h2>Persistence</h2>
 * Canonization requests are persisted to Neo4j as {@code CanonizationRequest} nodes with
 * {@code CANONIZATION_REQUEST_FOR} relationships to the target proposition. This replaces
 * the earlier in-memory ConcurrentHashMap approach and provides an audit trail.
 */
@Service
public class CanonizationGate {

    private static final Logger logger = LoggerFactory.getLogger(CanonizationGate.class);

    private final MemoryUnitRepository repository;
    private final CanonizationRequestRepository canonizationRequestRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ArcMemProperties.UnitConfig config;

    public CanonizationGate(MemoryUnitRepository repository,
                            CanonizationRequestRepository canonizationRequestRepository,
                            ApplicationEventPublisher eventPublisher,
                            ArcMemProperties properties) {
        this.repository = repository;
        this.canonizationRequestRepository = canonizationRequestRepository;
        this.eventPublisher = eventPublisher;
        this.config = properties.unit();
    }

    /**
     * Request promotion of an unit to CANON authority.
     * <p>
     * If a pending request already exists for this unit, the existing request is returned
     * (idempotency). If auto-approve is enabled for simulation contexts, the request is
     * immediately approved.
     *
     * @param unitId           the unit to canonize
     * @param contextId        the context the unit belongs to
     * @param unitText         snapshot of unit text at request time
     * @param currentAuthority the unit's current authority (pre-transition)
     * @param reason           human-readable reason for the transition
     * @param requestedBy      identifier of the requesting actor
     *
     * @return the created (or existing) CanonizationRequest
     */
    public CanonizationRequest requestCanonization(String unitId, String contextId,
                                                   String unitText, Authority currentAuthority,
                                                   String reason, String requestedBy) {
        return createRequest(unitId, contextId, unitText, currentAuthority, Authority.CANON,
                             reason, requestedBy);
    }

    /**
     * Request demotion of a CANON unit to the next lower authority level.
     * <p>
     * If a pending request already exists for this unit, the existing request is returned.
     *
     * @param unitId      the unit to decanonize
     * @param contextId   the context the unit belongs to
     * @param unitText    snapshot of unit text at request time
     * @param reason      human-readable reason (e.g., DemotionReason name)
     * @param requestedBy identifier of the requesting actor
     *
     * @return the created (or existing) CanonizationRequest
     */
    public CanonizationRequest requestDecanonization(String unitId, String contextId,
                                                     String unitText, String reason,
                                                     String requestedBy) {
        return createRequest(unitId, contextId, unitText, Authority.CANON,
                             Authority.CANON.previousLevel(), reason, requestedBy);
    }

    /**
     * Approve a pending canonization request and execute the authority transition.
     * <p>
     * Stale validation: if the unit's current authority no longer matches the
     * {@link CanonizationRequest#currentAuthority()}, the request is marked STALE and
     * the transition is not applied.
     *
     * @param requestId the ID of the request to approve
     *
     * @return the updated request, or empty if not found
     */
    public Optional<CanonizationRequest> approve(String requestId) {
        return approve(requestId, null, null);
    }

    /**
     * Approve a pending canonization request with audit trail.
     *
     * @param requestId  the ID of the request to approve
     * @param resolvedBy identifier of the resolving actor (nullable)
     * @param note       optional resolution note (nullable)
     *
     * @return the updated request, or empty if not found
     */
    public Optional<CanonizationRequest> approve(String requestId, String resolvedBy, String note) {
        var requestOpt = loadRequest(requestId);
        if (requestOpt.isEmpty()) {
            logger.warn("Cannot approve: request {} not found", requestId);
            return Optional.empty();
        }
        var request = requestOpt.get();
        if (request.status() != CanonizationStatus.PENDING) {
            logger.warn("Cannot approve: request {} is in state {}", requestId, request.status());
            return Optional.of(request);
        }

        // Stale check: unit authority may have changed since request was created
        var nodeOpt = repository.findPropositionNodeById(request.unitId());
        if (nodeOpt.isEmpty()) {
            logger.warn("Cannot approve: unit {} not found", request.unitId());
            canonizationRequestRepository.resolveCanonizationRequest(requestId, CanonizationStatus.STALE.name(), resolvedBy, note);
            return Optional.of(withStatus(request, CanonizationStatus.STALE, resolvedBy, note));
        }
        var currentAuthStr = nodeOpt.get().getAuthority();
        var currentAuth = currentAuthStr != null
                ? Authority.valueOf(currentAuthStr) : Authority.PROVISIONAL;
        if (currentAuth != request.currentAuthority()) {
            logger.warn("Stale canonization request {}: expected {} but found {}",
                        requestId, request.currentAuthority(), currentAuth);
            canonizationRequestRepository.resolveCanonizationRequest(requestId, CanonizationStatus.STALE.name(), resolvedBy, note);
            return Optional.of(withStatus(request, CanonizationStatus.STALE, resolvedBy, note));
        }

        repository.setAuthority(request.unitId(), request.requestedAuthority().name());
        var direction = request.requestedAuthority().level() > request.currentAuthority().level()
                ? AuthorityChangeDirection.PROMOTED : AuthorityChangeDirection.DEMOTED;
        try {
            eventPublisher.publishEvent(MemoryUnitLifecycleEvent.authorityChanged(
                    this, request.contextId(), request.unitId(),
                    request.currentAuthority(), request.requestedAuthority(),
                    direction, request.reason()));
        } catch (Exception e) {
            logger.warn("Failed to publish AuthorityChanged event after canonization approval: {}", e.getMessage());
        }

        canonizationRequestRepository.resolveCanonizationRequest(requestId, CanonizationStatus.APPROVED.name(), resolvedBy, note);
        var approved = withStatus(request, CanonizationStatus.APPROVED, resolvedBy, note);
        logger.info("Canonization request {} approved: unit {} {} -> {}",
                    requestId, request.unitId(), request.currentAuthority(), request.requestedAuthority());
        return Optional.of(approved);
    }

    /**
     * Reject a pending canonization request. The unit's authority is unchanged.
     *
     * @param requestId the ID of the request to reject
     *
     * @return the updated request, or empty if not found
     */
    public Optional<CanonizationRequest> reject(String requestId) {
        return reject(requestId, null, null);
    }

    /**
     * Reject a pending canonization request with audit trail.
     *
     * @param requestId  the ID of the request to reject
     * @param resolvedBy identifier of the resolving actor (nullable)
     * @param note       optional resolution note (nullable)
     *
     * @return the updated request, or empty if not found
     */
    public Optional<CanonizationRequest> reject(String requestId, String resolvedBy, String note) {
        var requestOpt = loadRequest(requestId);
        if (requestOpt.isEmpty()) {
            logger.warn("Cannot reject: request {} not found", requestId);
            return Optional.empty();
        }
        var request = requestOpt.get();
        if (request.status() != CanonizationStatus.PENDING) {
            logger.warn("Cannot reject: request {} is in state {}", requestId, request.status());
            return Optional.of(request);
        }
        canonizationRequestRepository.resolveCanonizationRequest(requestId, CanonizationStatus.REJECTED.name(), resolvedBy, note);
        var rejected = withStatus(request, CanonizationStatus.REJECTED, resolvedBy, note);
        logger.info("Canonization request {} rejected for unit {}", requestId, request.unitId());
        return Optional.of(rejected);
    }

    /**
     * Returns all pending canonization requests across all contexts.
     */
    public List<CanonizationRequest> pendingRequests() {
        return findAllPendingRequests();
    }

    /**
     * Returns pending canonization requests for a specific context.
     *
     * @param contextId the context to filter by
     */
    public List<CanonizationRequest> pendingRequests(String contextId) {
        return canonizationRequestRepository.findPendingCanonizationRequests(contextId).stream()
                                            .map(this::toCanonizationRequest)
                                            .toList();
    }

    /**
     * Mark all PENDING requests for a context as STALE.
     * Should be called during context teardown to prevent orphaned pending requests.
     *
     * @param contextId the context to clean up
     */
    public void markContextRequestsStale(String contextId) {
        canonizationRequestRepository.markContextRequestsStale(contextId);
    }

    private CanonizationRequest createRequest(String unitId, String contextId,
                                              String unitText, Authority currentAuthority,
                                              Authority requestedAuthority,
                                              String reason, String requestedBy) {
        // Idempotency: return existing pending request rather than creating a duplicate
        var existingOpt = canonizationRequestRepository.findPendingCanonizationRequest(unitId, requestedAuthority.name());
        if (existingOpt.isPresent()) {
            var existing = toCanonizationRequest(existingOpt.get());
            logger.debug("Returning existing pending canonization request {} for unit {}",
                         existing.id(), unitId);
            return existing;
        }

        var id = UUID.randomUUID().toString();
        canonizationRequestRepository.createCanonizationRequest(id, unitId, contextId, unitText,
                                                                currentAuthority.name(), requestedAuthority.name(), reason, requestedBy);

        var request = new CanonizationRequest(
                id, unitId, contextId, unitText,
                currentAuthority, requestedAuthority,
                reason, requestedBy,
                Instant.now(),
                CanonizationStatus.PENDING,
                null, null, null
        );
        logger.info("Canonization request {} created: unit {} {} -> {} (requestedBy={})",
                    request.id(), unitId, currentAuthority, requestedAuthority, requestedBy);

        // Auto-approve promotions only. Decanonization always requires HITL — CANON is world-defining (A3b).
        if (config.autoApprovePromotions() && requestedAuthority == Authority.CANON) {
            logger.debug("Auto-approving canonization promotion {} for unit {}", request.id(), unitId);
            return approve(request.id()).orElse(request);
        }

        return request;
    }

    private Optional<CanonizationRequest> loadRequest(String requestId) {
        return canonizationRequestRepository.findCanonizationRequestById(requestId).map(this::toCanonizationRequest);
    }

    private List<CanonizationRequest> findAllPendingRequests() {
        return canonizationRequestRepository.findAllPendingCanonizationRequests().stream()
                                            .map(this::toCanonizationRequest)
                                            .toList();
    }

    private CanonizationRequest toCanonizationRequest(Map<String, Object> row) {
        var id = (String) row.get("id");
        var unitId = (String) row.get("unitId");
        var ctxId = (String) row.get("contextId");
        var unitText = (String) row.get("unitText");
        var currentAuth = Authority.valueOf((String) row.get("currentAuthority"));
        var requestedAuth = Authority.valueOf((String) row.get("requestedAuthority"));
        var reason = (String) row.get("reason");
        var requestedBy = (String) row.get("requestedBy");
        var statusStr = (String) row.get("status");
        var status = CanonizationStatus.valueOf(statusStr);
        var createdAtStr = (String) row.get("createdAt");
        var createdAt = createdAtStr != null ? parseInstant(createdAtStr) : Instant.now();
        var resolvedAtStr = (String) row.get("resolvedAt");
        var resolvedAt = resolvedAtStr != null ? parseInstant(resolvedAtStr) : null;
        var resolvedBy = (String) row.get("resolvedBy");
        var resolutionNote = (String) row.get("resolutionNote");
        return new CanonizationRequest(id, unitId, ctxId, unitText,
                                       currentAuth, requestedAuth, reason, requestedBy,
                                       createdAt, status, resolvedAt, resolvedBy, resolutionNote);
    }

    private static Instant parseInstant(String text) {
        try {
            return Instant.parse(text);
        } catch (Exception e) {
            // Neo4j datetime strings may not be ISO-8601 compliant; fall back
            return Instant.now();
        }
    }

    private static CanonizationRequest withStatus(CanonizationRequest r, CanonizationStatus newStatus,
                                                  String resolvedBy, String note) {
        return new CanonizationRequest(r.id(), r.unitId(), r.contextId(), r.unitText(),
                                       r.currentAuthority(), r.requestedAuthority(), r.reason(), r.requestedBy(),
                                       r.createdAt(), newStatus,
                                       Instant.now(), resolvedBy, note);
    }
}
