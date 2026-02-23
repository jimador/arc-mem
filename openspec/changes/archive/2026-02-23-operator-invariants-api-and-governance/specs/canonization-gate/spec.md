## MODIFIED Requirements

### Requirement: Canonization gate service

**Modifies**: `CanonizationGate` service — migrates from in-memory `ConcurrentHashMap` storage to Neo4j persistence.

The `CanonizationGate` service SHALL persist all `CanonizationRequest` records to Neo4j instead of holding them in a `ConcurrentHashMap`. The service SHALL maintain the same public API contract:

- `requestCanonization(anchorId, contextId, anchorText, currentAuthority, reason, requestedBy)` -- creates a PENDING request and returns the request
- `requestDecanonization(anchorId, contextId, anchorText, reason, requestedBy)` -- creates a PENDING decanonization request and returns the request
- `approve(requestId)` -- executes the authority change and marks the request APPROVED
- `reject(requestId)` -- marks the request REJECTED without modifying the anchor
- `pendingRequests()` -- returns all PENDING requests
- `pendingRequests(contextId)` -- returns PENDING requests for a specific context

The `ConcurrentHashMap<String, CanonizationRequest> requests` field SHALL be removed. All CRUD operations SHALL delegate to a `CanonizationRequestRepository` backed by Neo4j via Drivine.

#### Scenario: Request survives application restart

- **GIVEN** a PENDING canonization request for anchor "A1" is created
- **WHEN** the application is restarted
- **THEN** the PENDING request SHALL still be retrievable via `pendingRequests()`

#### Scenario: Approve persisted request

- **GIVEN** a PENDING canonization request persisted in Neo4j for anchor "A1" requesting CANON
- **WHEN** a human approves the request
- **THEN** the anchor's authority is set to CANON, the request status in Neo4j becomes APPROVED, and an `AuthorityChanged` lifecycle event is published with direction PROMOTED

#### Scenario: Reject persisted request

- **GIVEN** a PENDING canonization request persisted in Neo4j for anchor "A1"
- **WHEN** a human rejects the request
- **THEN** the request status in Neo4j becomes REJECTED and the anchor's authority remains unchanged

#### Scenario: Stale request detection with persisted data

- **GIVEN** a PENDING canonization request persisted in Neo4j with `currentAuthority = RELIABLE`
- **AND** the anchor has since been demoted to UNRELIABLE
- **WHEN** approval is attempted
- **THEN** the request SHALL be marked STALE in Neo4j and the authority change SHALL NOT be applied

### Requirement: CanonizationRequestRepository

The system SHALL provide a `CanonizationRequestRepository` interface with the following methods, backed by Drivine and Neo4j:

- `save(CanonizationRequest request)` -- persists a new request or updates an existing one
- `findById(String requestId)` -- returns `Optional<CanonizationRequest>`
- `findByStatus(CanonizationStatus status)` -- returns all requests with the given status
- `findByStatusAndContextId(CanonizationStatus status, String contextId)` -- returns requests filtered by status and context
- `findPendingByAnchorIdAndRequestedAuthority(String anchorId, Authority requestedAuthority)` -- for idempotency check
- `updateStatus(String requestId, CanonizationStatus newStatus)` -- updates only the status field

The Neo4j node label SHALL be `CanonizationRequest`. Properties SHALL map directly from the `CanonizationRequest` record fields with `Authority` and `CanonizationStatus` stored as string values.

#### Scenario: Save and retrieve request

- **GIVEN** a new `CanonizationRequest` record
- **WHEN** `save(request)` is called followed by `findById(request.id())`
- **THEN** the returned request SHALL have identical field values to the saved request

#### Scenario: Find pending by anchor ID for idempotency

- **GIVEN** a PENDING request exists for anchor "A1" requesting CANON
- **WHEN** `findPendingByAnchorIdAndRequestedAuthority("A1", CANON)` is called
- **THEN** the existing request SHALL be returned

### Requirement: Canonization audit trail

The system SHALL record an audit trail for all canonization request state transitions. Each transition SHALL be persisted as a `CanonizationAuditEntry` with the following fields:

| Field | Type | Description |
|-------|------|-------------|
| `entryId` | String | Unique audit entry ID |
| `requestId` | String | The canonization request this entry belongs to |
| `previousStatus` | CanonizationStatus | Status before the transition |
| `newStatus` | CanonizationStatus | Status after the transition |
| `actor` | String | Identifier of who performed the action (e.g., `"human"`, `"system"`, `"auto-approve"`) |
| `reason` | String (nullable) | Reason for rejection or stale detection; null for approvals |
| `occurredAt` | Instant | Timestamp of the transition |

Audit entries SHALL be persisted to Neo4j as `CanonizationAudit` nodes with a `AUDIT_OF` relationship to the `CanonizationRequest` node.

The `CanonizationGate` SHALL create audit entries for every status transition: PENDING to APPROVED, PENDING to REJECTED, PENDING to STALE.

#### Scenario: Approval creates audit entry

- **GIVEN** a PENDING canonization request "R1"
- **WHEN** the request is approved
- **THEN** a `CanonizationAuditEntry` SHALL be persisted with `previousStatus = PENDING`, `newStatus = APPROVED`, and `actor = "human"`

#### Scenario: Rejection creates audit entry with reason

- **GIVEN** a PENDING canonization request "R1"
- **WHEN** the request is rejected with reason "Not established enough"
- **THEN** a `CanonizationAuditEntry` SHALL be persisted with `previousStatus = PENDING`, `newStatus = REJECTED`, `reason = "Not established enough"`, and `actor = "human"`

#### Scenario: Stale detection creates audit entry

- **GIVEN** a PENDING canonization request "R1" with `currentAuthority = RELIABLE`
- **AND** the anchor has been demoted to UNRELIABLE
- **WHEN** approval is attempted
- **THEN** a `CanonizationAuditEntry` SHALL be persisted with `previousStatus = PENDING`, `newStatus = STALE`, `reason = "stale: authority changed from RELIABLE to UNRELIABLE"`, and `actor = "system"`

#### Scenario: Auto-approve in simulation creates audit entry

- **GIVEN** a PENDING canonization request in simulation context "sim-abc-123" with auto-approve enabled
- **WHEN** the request is auto-approved
- **THEN** a `CanonizationAuditEntry` SHALL be persisted with `actor = "auto-approve"` and `newStatus = APPROVED`

#### Scenario: Audit trail queryable per request

- **GIVEN** canonization request "R1" has been approved, and request "R2" has been rejected
- **WHEN** the audit trail for "R1" is queried
- **THEN** only audit entries for "R1" SHALL be returned

### Requirement: Canonization context cleanup

When a simulation context is cleaned up (via `SimulationService` teardown), all canonization requests associated with that context SHALL have their PENDING requests marked STALE with reason `"context-cleanup"`. APPROVED and REJECTED requests SHALL be retained for audit purposes.

#### Scenario: Pending requests marked stale on simulation cleanup

- **GIVEN** two PENDING canonization requests exist for simulation context "sim-abc-123"
- **WHEN** the simulation completes and context cleanup runs
- **THEN** both requests SHALL be marked STALE with reason `"context-cleanup"` and corresponding audit entries SHALL be created

#### Scenario: Approved requests retained on cleanup

- **GIVEN** an APPROVED canonization request exists for simulation context "sim-abc-123"
- **WHEN** context cleanup runs
- **THEN** the request SHALL remain APPROVED and no additional audit entry SHALL be created
