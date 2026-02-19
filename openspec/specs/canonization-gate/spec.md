## ADDED Requirements

### Requirement: Canonization request model

The system SHALL represent pending CANON authority transitions as `CanonizationRequest` records containing: a unique request ID, anchor ID, context ID, snapshot of anchor text, current authority, requested authority, reason string, requester identifier, creation timestamp, and status (PENDING, APPROVED, REJECTED).

The requested authority SHALL be CANON for canonization requests and the level immediately below CANON (RELIABLE) for decanonization requests.

#### Scenario: Canonization request created for promotion to CANON

- **GIVEN** an anchor at RELIABLE authority
- **WHEN** a promotion to CANON is requested (via tool call, conflict resolution, or API)
- **THEN** a `CanonizationRequest` is created with status PENDING, current authority RELIABLE, requested authority CANON, and the reason and requester recorded

#### Scenario: Decanonization request created for demotion from CANON

- **GIVEN** an anchor at CANON authority
- **WHEN** a demotion from CANON is requested (via conflict resolution DEMOTE_EXISTING, manual tool call, or API)
- **THEN** a `CanonizationRequest` is created with status PENDING, current authority CANON, requested authority RELIABLE, and the reason and requester recorded

### Requirement: Canonization gate service

The system SHALL provide a `CanonizationGate` service with the following operations:

- `requestCanonization(anchorId, contextId, currentAuthority, requestedAuthority, reason, requestedBy)` — creates a PENDING request and returns the request ID
- `approve(requestId)` — executes the authority change via `AnchorEngine.setAuthority()` and marks the request APPROVED
- `reject(requestId, rejectionReason)` — marks the request REJECTED without modifying the anchor
- `pendingRequests()` — returns all PENDING requests
- `pendingRequests(contextId)` — returns PENDING requests for a specific context

Pending requests SHALL be stored in memory (`ConcurrentHashMap`). Requests are transient and lost on application restart.

#### Scenario: Approve canonization request

- **GIVEN** a PENDING canonization request for anchor "A1" requesting CANON
- **WHEN** a human approves the request
- **THEN** the anchor's authority is set to CANON, the request status becomes APPROVED, and an `AuthorityChanged` lifecycle event is published with direction PROMOTED

#### Scenario: Reject canonization request

- **GIVEN** a PENDING canonization request for anchor "A1" requesting CANON
- **WHEN** a human rejects the request with reason "Not established enough"
- **THEN** the anchor's authority remains unchanged, the request status becomes REJECTED, and the rejection reason is recorded

#### Scenario: Approve decanonization request

- **GIVEN** a PENDING decanonization request for anchor "A1" requesting demotion from CANON to RELIABLE
- **WHEN** a human approves the request
- **THEN** the anchor's authority is set to RELIABLE, the request status becomes APPROVED, and an `AuthorityChanged` lifecycle event is published with direction DEMOTED

#### Scenario: List pending requests

- **GIVEN** three pending requests exist, two for context "ctx-1" and one for context "ctx-2"
- **WHEN** `pendingRequests("ctx-1")` is called
- **THEN** only the two requests for context "ctx-1" are returned

### Requirement: Stale request validation

When `approve()` is called, the `CanonizationGate` SHALL verify that the anchor's current authority still matches the `currentAuthority` recorded in the `CanonizationRequest`. If the anchor's authority has changed since the request was created (e.g., demoted by conflict resolution while the request was pending), the request SHALL be rejected as stale.

This prevents a pending canonization from promoting an anchor that has been demoted since the request was created (e.g., RELIABLE → UNRELIABLE via conflict, then approved canonization would jump UNRELIABLE → CANON, bypassing the gate's intent).

#### Scenario: Approve stale request rejected

- **GIVEN** a PENDING canonization request for anchor "A1" with currentAuthority RELIABLE
- **AND** anchor "A1" has since been demoted to UNRELIABLE (via conflict resolution)
- **WHEN** a human approves the request
- **THEN** the approval is rejected with a descriptive error indicating the anchor's authority has changed
- **AND** the request status becomes REJECTED with reason "stale: authority changed from RELIABLE to UNRELIABLE"

#### Scenario: Approve request with unchanged authority succeeds

- **GIVEN** a PENDING canonization request for anchor "A1" with currentAuthority RELIABLE
- **AND** anchor "A1" is still at RELIABLE authority
- **WHEN** a human approves the request
- **THEN** the authority change executes normally

### Requirement: Canonization request idempotency

The `CanonizationGate` SHALL prevent duplicate pending requests for the same anchor. If a pending request already exists for an anchor, `requestCanonization()` SHALL return the existing request ID rather than creating a new request.

Approved and rejected requests do not block new requests for the same anchor.

#### Scenario: Duplicate pending request returns existing ID

- **GIVEN** a PENDING canonization request exists for anchor "A1"
- **WHEN** another canonization request is created for anchor "A1"
- **THEN** the existing request ID is returned and no new request is created

#### Scenario: New request after rejection allowed

- **GIVEN** a REJECTED canonization request exists for anchor "A1"
- **WHEN** a new canonization request is created for anchor "A1"
- **THEN** a new PENDING request is created with a new request ID

### Requirement: Gate intercept in AnchorEngine

`AnchorEngine` SHALL delegate to `CanonizationGate` for all authority transitions involving CANON when the gate is enabled.

- When `promote()` is called with CANON authority: the anchor SHALL be promoted at RELIABLE (the highest auto-assignable level) and a pending canonization request SHALL be created.
- When `demote()` targets a CANON anchor: the anchor SHALL remain at CANON and a pending decanonization request SHALL be created.
- Seed anchors defined in scenario YAML files with `authority: CANON` SHALL bypass the gate (the human authored the scenario, so approval is implicit).

#### Scenario: Promote to CANON intercepted by gate

- **GIVEN** the canonization gate is enabled
- **WHEN** a caller requests promotion of anchor "A1" to CANON authority
- **THEN** the anchor is promoted at RELIABLE authority AND a PENDING canonization request is created for the CANON transition

#### Scenario: Demote from CANON intercepted by gate

- **GIVEN** the canonization gate is enabled and anchor "A1" is at CANON authority
- **WHEN** conflict resolution returns DEMOTE_EXISTING for anchor "A1"
- **THEN** the anchor remains at CANON AND a PENDING decanonization request is created

#### Scenario: Seed anchors bypass gate

- **GIVEN** a simulation scenario YAML defines a seed anchor with `authority: CANON`
- **WHEN** the simulation engine initializes seed anchors
- **THEN** the anchor is created at CANON authority without a pending request

#### Scenario: Gate disabled — promotion to CANON executes immediately

- **GIVEN** `dice-anchors.anchor.canonization-gate-enabled` is set to `false`
- **WHEN** a promotion to CANON is requested
- **THEN** the authority is set to CANON immediately without creating a pending request

#### Scenario: Gate disabled — demotion from CANON executes immediately

- **GIVEN** `dice-anchors.anchor.canonization-gate-enabled` is set to `false` and anchor "A1" is at CANON authority
- **WHEN** a demotion from CANON is requested (via conflict resolution, manual tool call, or trust degradation)
- **THEN** the anchor is demoted to RELIABLE immediately without creating a pending request
- **AND** invariant A3b (CANON immune to automatic demotion) does NOT apply when the gate is disabled — the operator has explicitly opted out of CANON protection by disabling the gate

### Requirement: Simulation auto-approve configuration

In simulation mode, the canonization gate SHALL auto-approve pending requests by default. This is configurable via `dice-anchors.anchor.auto-approve-in-simulation` (default: `true`).

When auto-approve is enabled and the context ID matches a simulation context (`sim-*`), the gate SHALL immediately approve requests without human intervention.

#### Scenario: Auto-approve enabled in simulation

- **GIVEN** auto-approve is enabled (default) and the context ID is "sim-abc-123"
- **WHEN** a canonization request is created
- **THEN** the request is automatically approved and the authority change executes immediately

#### Scenario: Auto-approve disabled in simulation

- **GIVEN** `dice-anchors.anchor.auto-approve-in-simulation` is set to `false`
- **WHEN** a canonization request is created for a simulation context
- **THEN** the request remains PENDING until explicitly approved or rejected

### Requirement: Configuration properties

The system SHALL support the following configuration properties:

- `dice-anchors.anchor.canonization-gate-enabled` (boolean, default: `true`) — enables or disables the HITL gate for CANON transitions
- `dice-anchors.anchor.auto-approve-in-simulation` (boolean, default: `true`) — auto-approves CANON transitions in simulation contexts

#### Scenario: Default configuration

- **WHEN** no canonization gate properties are specified
- **THEN** the gate is enabled and simulation auto-approve is enabled
