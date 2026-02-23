package dev.dunnam.diceanchors.anchor;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An in-flight request to change an anchor's authority to or from CANON.
 * <p>
 * The canonization gate holds pending requests until a human approves or rejects them.
 * This provides a human-in-the-loop (HITL) control point for the highest-trust authority
 * level, preventing automatic promotion to CANON (invariant A3a) and protecting CANON
 * anchors from automatic demotion (invariant A3b).
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Created with status {@link CanonizationStatus#PENDING} when a CANON transition
 *       is requested.</li>
 *   <li>Transitions to {@link CanonizationStatus#APPROVED} or
 *       {@link CanonizationStatus#REJECTED} on human action.</li>
 *   <li>Transitions to {@link CanonizationStatus#STALE} if the anchor's authority
 *       changes before the request is acted upon.</li>
 * </ol>
 *
 * @param id                  unique request ID (UUID string)
 * @param anchorId            ID of the anchor whose authority is changing
 * @param contextId           conversation or simulation context the anchor belongs to
 * @param anchorText          snapshot of the anchor text at request creation time
 * @param currentAuthority    authority level at the time the request was created
 * @param requestedAuthority  authority level being requested (always CANON for promotions;
 *                            always the previous level for decanonizations)
 * @param reason              human-readable description of why this transition is requested
 * @param requestedBy         identifier of the actor requesting the change (e.g., "system",
 *                            "user", "llm-tool")
 * @param createdAt           timestamp when the request was created
 * @param status              current lifecycle status of this request
 * @param resolvedAt          timestamp when the request was approved, rejected, or marked stale (null while PENDING)
 * @param resolvedBy          identifier of the actor who resolved the request (null while PENDING)
 * @param resolutionNote      optional note attached when the request is resolved (null while PENDING)
 */
public record CanonizationRequest(
        String id,
        String anchorId,
        String contextId,
        String anchorText,
        Authority currentAuthority,
        Authority requestedAuthority,
        String reason,
        String requestedBy,
        Instant createdAt,
        CanonizationStatus status,
        @Nullable Instant resolvedAt,
        @Nullable String resolvedBy,
        @Nullable String resolutionNote
) {}
