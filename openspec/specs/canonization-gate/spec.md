## ADDED Requirements

### Requirement: Canonization request model

The system SHALL represent pending CANON authority transitions as `CanonizationRequest` records containing: a unique request ID, ARC Working Memory Unit (AWMU) ID, context ID, snapshot of AWMU text, current authority, requested authority, reason string, requester identifier, creation timestamp, and status (PENDING, APPROVED, REJECTED).

The requested authority SHALL be CANON for canonization requests and the level immediately below CANON (RELIABLE) for decanonization requests.

#### Scenario: Canonization request created for promotion to CANON

- **GIVEN** a AWMU at RELIABLE authority
- **WHEN** a promotion to CANON is requested (via tool call, conflict resolution, or API)
- **THEN** a `CanonizationRequest` is created with status PENDING, current authority RELIABLE, requested authority CANON, and the reason and requester recorded

#### Scenario: Decanonization request created for demotion from CANON

- **GIVEN** a AWMU at CANON authority
- **WHEN** a demotion from CANON is requested (via conflict resolution DEMOTE_EXISTING, manual tool call, or API)
- **THEN** a `CanonizationRequest` is created with status PENDING, current authority CANON, requested authority RELIABLE, and the reason and requester recorded

### Requirement: Canonization gate service

The system SHALL provide a `CanonizationGate` service with the following operations:

- `requestCanonization(unitId, contextId, currentAuthority, requestedAuthority, reason, requestedBy)` — creates a PENDING request and returns the request ID
- `approve(requestId)` — executes the authority change via `ArcMemEngine.setAuthority()` and marks the request APPROVED
- `reject(requestId, rejectionReason)` — marks the request REJECTED without modifying the AWMU
- `pendingRequests()` — returns all PENDING requests
- `pendingRequests(contextId)` — returns PENDING requests for a specific context

Pending requests SHALL be stored in memory (`ConcurrentHashMap`). Requests are transient and lost on application restart.

#### Scenario: Approve canonization request

- **GIVEN** a PENDING canonization request for AWMU "A1" requesting CANON
- **WHEN** a human approves the request
- **THEN** the AWMU's authority is set to CANON, the request status becomes APPROVED, and an `AuthorityChanged` lifecycle event is published with direction PROMOTED

#### Scenario: Reject canonization request

- **GIVEN** a PENDING canonization request for AWMU "A1" requesting CANON
- **WHEN** a human rejects the request with reason "Not established enough"
- **THEN** the AWMU's authority remains unchanged, the request status becomes REJECTED, and the rejection reason is recorded

#### Scenario: Approve decanonization request

- **GIVEN** a PENDING decanonization request for AWMU "A1" requesting demotion from CANON to RELIABLE
- **WHEN** a human approves the request
- **THEN** the AWMU's authority is set to RELIABLE, the request status becomes APPROVED, and an `AuthorityChanged` lifecycle event is published with direction DEMOTED

#### Scenario: List pending requests

- **GIVEN** three pending requests exist, two for context "ctx-1" and one for context "ctx-2"
- **WHEN** `pendingRequests("ctx-1")` is called
- **THEN** only the two requests for context "ctx-1" are returned

### Requirement: Stale request validation

When `approve()` is called, the `CanonizationGate` SHALL verify that the AWMU's current authority still matches the `currentAuthority` recorded in the `CanonizationRequest`. If the AWMU's authority has changed since the request was created (e.g., demoted by conflict resolution while the request was pending), the request SHALL be rejected as stale.

This prevents a pending canonization from promoting a AWMU that has been demoted since the request was created (e.g., RELIABLE → UNRELIABLE via conflict, then approved canonization would jump UNRELIABLE → CANON, bypassing the gate's intent).

#### Scenario: Approve stale request rejected

- **GIVEN** a PENDING canonization request for AWMU "A1" with currentAuthority RELIABLE
- **AND** AWMU "A1" has since been demoted to UNRELIABLE (via conflict resolution)
- **WHEN** a human approves the request
- **THEN** the approval is rejected with a descriptive error indicating the AWMU's authority has changed
- **AND** the request status becomes REJECTED with reason "stale: authority changed from RELIABLE to UNRELIABLE"

#### Scenario: Approve request with unchanged authority succeeds

- **GIVEN** a PENDING canonization request for AWMU "A1" with currentAuthority RELIABLE
- **AND** AWMU "A1" is still at RELIABLE authority
- **WHEN** a human approves the request
- **THEN** the authority change executes normally

### Requirement: Canonization request idempotency

The `CanonizationGate` SHALL prevent duplicate pending requests for the same AWMU. If a pending request already exists for a AWMU, `requestCanonization()` SHALL return the existing request ID rather than creating a new request.

Approved and rejected requests do not block new requests for the same AWMU.

#### Scenario: Duplicate pending request returns existing ID

- **GIVEN** a PENDING canonization request exists for AWMU "A1"
- **WHEN** another canonization request is created for AWMU "A1"
- **THEN** the existing request ID is returned and no new request is created

#### Scenario: New request after rejection allowed

- **GIVEN** a REJECTED canonization request exists for AWMU "A1"
- **WHEN** a new canonization request is created for AWMU "A1"
- **THEN** a new PENDING request is created with a new request ID

### Requirement: Gate intercept in ArcMemEngine

`ArcMemEngine` SHALL delegate to `CanonizationGate` for all authority transitions involving CANON when the gate is enabled.

- When `promote()` is called with CANON authority: the AWMU SHALL be promoted at RELIABLE (the highest auto-assignable level) and a pending canonization request SHALL be created.
- When `demote()` targets a CANON AWMU: the AWMU SHALL remain at CANON and a pending decanonization request SHALL be created.
- Seed units defined in scenario YAML files with `authority: CANON` SHALL bypass the gate (the human authored the scenario, so approval is implicit).

#### Scenario: Promote to CANON intercepted by gate

- **GIVEN** the canonization gate is enabled
- **WHEN** a caller requests promotion of AWMU "A1" to CANON authority
- **THEN** the AWMU is promoted at RELIABLE authority AND a PENDING canonization request is created for the CANON transition

#### Scenario: Demote from CANON intercepted by gate

- **GIVEN** the canonization gate is enabled and AWMU "A1" is at CANON authority
- **WHEN** conflict resolution returns DEMOTE_EXISTING for AWMU "A1"
- **THEN** the AWMU remains at CANON AND a PENDING decanonization request is created

#### Scenario: Seed units bypass gate

- **GIVEN** a simulation scenario YAML defines a seed unit with `authority: CANON`
- **WHEN** the simulation engine initializes seed units
- **THEN** the AWMU is created at CANON authority without a pending request

#### Scenario: Gate disabled — promotion to CANON executes immediately

- **GIVEN** `arc-mem.unit.canonization-gate-enabled` is set to `false`
- **WHEN** a promotion to CANON is requested
- **THEN** the authority is set to CANON immediately without creating a pending request

#### Scenario: Gate disabled — demotion from CANON executes immediately

- **GIVEN** `arc-mem.unit.canonization-gate-enabled` is set to `false` and AWMU "A1" is at CANON authority
- **WHEN** a demotion from CANON is requested (via conflict resolution, manual tool call, or trust degradation)
- **THEN** the AWMU is demoted to RELIABLE immediately without creating a pending request
- **AND** invariant A3b (CANON immune to automatic demotion) does NOT apply when the gate is disabled — the operator has explicitly opted out of CANON protection by disabling the gate

## Operator Invariants (F07)

### Requirement: Canonization gate service (Neo4j persistence)

**Modifies**: `CanonizationGate` service — migrates from in-memory `ConcurrentHashMap` storage to Neo4j persistence.

The `CanonizationGate` service SHALL persist all `CanonizationRequest` records to Neo4j instead of holding them in a `ConcurrentHashMap`. The service SHALL maintain the same public API contract:

- `requestCanonization(unitId, contextId, unitText, currentAuthority, reason, requestedBy)` -- creates a PENDING request and returns the request
- `requestDecanonization(unitId, contextId, unitText, reason, requestedBy)` -- creates a PENDING decanonization request and returns the request
- `approve(requestId)` -- executes the authority change and marks the request APPROVED
- `reject(requestId)` -- marks the request REJECTED without modifying the AWMU
- `pendingRequests()` -- returns all PENDING requests
- `pendingRequests(contextId)` -- returns PENDING requests for a specific context

The `ConcurrentHashMap<String, CanonizationRequest> requests` field SHALL be removed. All CRUD operations SHALL delegate to a `CanonizationRequestRepository` backed by Neo4j via Drivine.

#### Scenario: Request survives application restart

- **GIVEN** a PENDING canonization request for AWMU "A1" is created
- **WHEN** the application is restarted
- **THEN** the PENDING request SHALL still be retrievable via `pendingRequests()`

#### Scenario: Approve persisted request

- **GIVEN** a PENDING canonization request persisted in Neo4j for AWMU "A1" requesting CANON
- **WHEN** a human approves the request
- **THEN** the AWMU's authority is set to CANON, the request status in Neo4j becomes APPROVED, and an `AuthorityChanged` lifecycle event is published with direction PROMOTED

#### Scenario: Reject persisted request

- **GIVEN** a PENDING canonization request persisted in Neo4j for AWMU "A1"
- **WHEN** a human rejects the request
- **THEN** the request status in Neo4j becomes REJECTED and the AWMU's authority remains unchanged

#### Scenario: Stale request detection with persisted data

- **GIVEN** a PENDING canonization request persisted in Neo4j with `currentAuthority = RELIABLE`
- **AND** the AWMU has since been demoted to UNRELIABLE
- **WHEN** approval is attempted
- **THEN** the request SHALL be marked STALE in Neo4j and the authority change SHALL NOT be applied

### Requirement: CanonizationRequestRepository

The system SHALL provide a `CanonizationRequestRepository` interface with the following methods, backed by Drivine and Neo4j:

- `save(CanonizationRequest request)` -- persists a new request or updates an existing one
- `findById(String requestId)` -- returns `Optional<CanonizationRequest>`
- `findByStatus(CanonizationStatus status)` -- returns all requests with the given status
- `findByStatusAndContextId(CanonizationStatus status, String contextId)` -- returns requests filtered by status and context
- `findPendingByUnitIdAndRequestedAuthority(String unitId, Authority requestedAuthority)` -- for idempotency check
- `updateStatus(String requestId, CanonizationStatus newStatus)` -- updates only the status field

The Neo4j node label SHALL be `CanonizationRequest`. Properties SHALL map directly from the `CanonizationRequest` record fields with `Authority` and `CanonizationStatus` stored as string values.

#### Scenario: Save and retrieve request

- **GIVEN** a new `CanonizationRequest` record
- **WHEN** `save(request)` is called followed by `findById(request.id())`
- **THEN** the returned request SHALL have identical field values to the saved request

#### Scenario: Find pending by AWMU ID for idempotency

- **GIVEN** a PENDING request exists for AWMU "A1" requesting CANON
- **WHEN** `findPendingByUnitIdAndRequestedAuthority("A1", CANON)` is called
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
- **AND** the AWMU has been demoted to UNRELIABLE
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

### Requirement: Simulation auto-approve configuration

In simulation mode, the canonization gate SHALL auto-approve pending requests by default. This is configurable via `arc-mem.unit.auto-approve-in-simulation` (default: `true`).

When auto-approve is enabled and the context ID matches a simulation context (`sim-*`), the gate SHALL immediately approve requests without human intervention.

#### Scenario: Auto-approve enabled in simulation

- **GIVEN** auto-approve is enabled (default) and the context ID is "sim-abc-123"
- **WHEN** a canonization request is created
- **THEN** the request is automatically approved and the authority change executes immediately

#### Scenario: Auto-approve disabled in simulation

- **GIVEN** `arc-mem.unit.auto-approve-in-simulation` is set to `false`
- **WHEN** a canonization request is created for a simulation context
- **THEN** the request remains PENDING until explicitly approved or rejected

### Requirement: Configuration properties

The system SHALL support the following configuration properties:

- `arc-mem.unit.canonization-gate-enabled` (boolean, default: `true`) — enables or disables the HITL gate for CANON transitions
- `arc-mem.unit.auto-approve-in-simulation` (boolean, default: `true`) — auto-approves CANON transitions in simulation contexts

#### Scenario: Default configuration

- **WHEN** no canonization gate properties are specified
- **THEN** the gate is enabled and simulation auto-approve is enabled
