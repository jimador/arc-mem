package dev.arcmem.core.persistence;

import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Drivine-backed repository for canonization request persistence.
 * <p>
 * Manages {@code CanonizationRequest} nodes and their {@code CANONIZATION_REQUEST_FOR}
 * relationships to target propositions in Neo4j.
 */
@Service
public class CanonizationRequestRepository {

    private static final Logger logger = LoggerFactory.getLogger(CanonizationRequestRepository.class);

    private final PersistenceManager persistenceManager;

    public CanonizationRequestRepository(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    /**
     * Create a canonization request node with a relationship to the target proposition.
     *
     * @param id                 unique request ID
     * @param unitId           the unit whose authority is changing
     * @param contextId          the context the unit belongs to
     * @param unitText         snapshot of unit text at request time
     * @param currentAuthority   authority at request creation
     * @param requestedAuthority target authority
     * @param reason             human-readable reason
     * @param requestedBy        actor requesting the change
     */
    @Transactional
    public void createCanonizationRequest(@NonNull String id, @NonNull String unitId,
                                           @NonNull String contextId, @NonNull String unitText,
                                           @NonNull String currentAuthority, @NonNull String requestedAuthority,
                                           @NonNull String reason, @NonNull String requestedBy) {
        var cypher = """
                CREATE (r:CanonizationRequest {
                    id: $id, unitId: $unitId, contextId: $contextId,
                    unitText: $unitText, currentAuthority: $currentAuthority,
                    requestedAuthority: $requestedAuthority, reason: $reason,
                    requestedBy: $requestedBy, status: 'PENDING',
                    createdAt: toString(datetime())
                })
                WITH r
                MATCH (p:Proposition {id: $unitId})
                CREATE (r)-[:CANONIZATION_REQUEST_FOR]->(p)
                RETURN {id: r.id} AS result
                """;
        var params = new HashMap<String, Object>();
        params.put("id", id);
        params.put("unitId", unitId);
        params.put("contextId", contextId);
        params.put("unitText", unitText);
        params.put("currentAuthority", currentAuthority);
        params.put("requestedAuthority", requestedAuthority);
        params.put("reason", reason);
        params.put("requestedBy", requestedBy);
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Created canonization request {} for unit {}", id, unitId);
        } catch (Exception e) {
            logger.error("Failed to create canonization request {} for unit {}: {}", id, unitId, e.getMessage(), e);
        }
    }

    /**
     * Find all pending canonization requests for a specific context.
     *
     * @param contextId the context to query
     * @return list of request data maps
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findPendingCanonizationRequests(@NonNull String contextId) {
        var cypher = """
                MATCH (r:CanonizationRequest {contextId: $contextId, status: 'PENDING'})
                RETURN {id: r.id, unitId: r.unitId, contextId: r.contextId,
                        unitText: r.unitText, currentAuthority: r.currentAuthority,
                        requestedAuthority: r.requestedAuthority, reason: r.reason,
                        requestedBy: r.requestedBy, status: r.status,
                        createdAt: toString(r.createdAt)} AS result
                """;
        var params = Map.of("contextId", contextId);
        try {
            @SuppressWarnings("unchecked")
            var rows = (List<Map<String, Object>>) (List<?>) persistenceManager.query(
                    QuerySpecification.withStatement(cypher).bind(params).transform(Map.class));
            return rows;
        } catch (Exception e) {
            logger.warn("findPendingCanonizationRequests query failed for context {}: {}", contextId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Find a pending canonization request for a specific unit and requested authority.
     * Used for idempotency checks.
     *
     * @param unitId           the unit ID
     * @param requestedAuthority the requested authority level
     * @return the request data map if found
     */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> findPendingCanonizationRequest(@NonNull String unitId,
                                                                         @NonNull String requestedAuthority) {
        var cypher = """
                MATCH (r:CanonizationRequest {unitId: $unitId, requestedAuthority: $requestedAuthority, status: 'PENDING'})
                RETURN {id: r.id, unitId: r.unitId, contextId: r.contextId,
                        unitText: r.unitText, currentAuthority: r.currentAuthority,
                        requestedAuthority: r.requestedAuthority, reason: r.reason,
                        requestedBy: r.requestedBy, status: r.status,
                        createdAt: toString(r.createdAt)} AS result
                LIMIT 1
                """;
        var params = Map.of("unitId", unitId, "requestedAuthority", requestedAuthority);
        try {
            @SuppressWarnings("unchecked")
            var rows = (List<Map<String, Object>>) (List<?>) persistenceManager.query(
                    QuerySpecification.withStatement(cypher).bind(params).transform(Map.class));
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
        } catch (Exception e) {
            logger.warn("findPendingCanonizationRequest query failed for unit {}: {}", unitId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Resolve a canonization request (approve/reject) with audit trail fields.
     *
     * @param requestId  the request ID
     * @param newStatus  the new status (APPROVED, REJECTED, STALE)
     * @param resolvedBy identifier of the resolving actor
     * @param note       optional resolution note
     */
    @Transactional
    public void resolveCanonizationRequest(@NonNull String requestId, @NonNull String newStatus,
                                            @Nullable String resolvedBy, @Nullable String note) {
        var cypher = """
                MATCH (r:CanonizationRequest {id: $id})
                SET r.status = $newStatus,
                    r.resolvedAt = toString(datetime()),
                    r.resolvedBy = $resolvedBy,
                    r.resolutionNote = $note
                """;
        var params = new HashMap<String, Object>();
        params.put("id", requestId);
        params.put("newStatus", newStatus);
        params.put("resolvedBy", resolvedBy);
        params.put("note", note);
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Resolved canonization request {} as {}", requestId, newStatus);
        } catch (Exception e) {
            logger.error("Failed to resolve canonization request {}: {}", requestId, e.getMessage(), e);
        }
    }

    /**
     * Find a canonization request by its ID (any status).
     *
     * @param requestId the request ID
     * @return the request data map if found
     */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> findCanonizationRequestById(@NonNull String requestId) {
        var cypher = """
                MATCH (r:CanonizationRequest {id: $id})
                RETURN {id: r.id, unitId: r.unitId, contextId: r.contextId,
                        unitText: r.unitText, currentAuthority: r.currentAuthority,
                        requestedAuthority: r.requestedAuthority, reason: r.reason,
                        requestedBy: r.requestedBy, status: r.status,
                        createdAt: toString(r.createdAt),
                        resolvedAt: toString(r.resolvedAt),
                        resolvedBy: r.resolvedBy,
                        resolutionNote: r.resolutionNote} AS result
                """;
        var params = Map.of("id", requestId);
        try {
            @SuppressWarnings("unchecked")
            var rows = (List<Map<String, Object>>) (List<?>) persistenceManager.query(
                    QuerySpecification.withStatement(cypher).bind(params).transform(Map.class));
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
        } catch (Exception e) {
            logger.warn("findCanonizationRequestById query failed for request {}: {}", requestId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Find all pending canonization requests across all contexts.
     *
     * @return list of pending request data maps
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findAllPendingCanonizationRequests() {
        var cypher = """
                MATCH (r:CanonizationRequest {status: 'PENDING'})
                RETURN {id: r.id, unitId: r.unitId, contextId: r.contextId,
                        unitText: r.unitText, currentAuthority: r.currentAuthority,
                        requestedAuthority: r.requestedAuthority, reason: r.reason,
                        requestedBy: r.requestedBy, status: r.status,
                        createdAt: toString(r.createdAt)} AS result
                """;
        try {
            @SuppressWarnings("unchecked")
            var rows = (List<Map<String, Object>>) (List<?>) persistenceManager.query(
                    QuerySpecification.withStatement(cypher).transform(Map.class));
            return rows;
        } catch (Exception e) {
            logger.warn("findAllPendingCanonizationRequests query failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Mark all PENDING canonization requests for a context as STALE.
     * Used during context teardown to prevent orphaned pending requests.
     *
     * @param contextId the context to clean up
     */
    @Transactional
    public void markContextRequestsStale(@NonNull String contextId) {
        var cypher = """
                MATCH (r:CanonizationRequest {contextId: $contextId, status: 'PENDING'})
                SET r.status = 'STALE',
                    r.resolvedAt = toString(datetime())
                """;
        var params = Map.of("contextId", contextId);
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Marked pending canonization requests as STALE for context {}", contextId);
        } catch (Exception e) {
            logger.warn("Failed to mark canonization requests as stale for context {}: {}", contextId, e.getMessage());
        }
    }
}
