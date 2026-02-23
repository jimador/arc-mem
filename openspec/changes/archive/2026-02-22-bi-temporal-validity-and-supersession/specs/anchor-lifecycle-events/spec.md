## ADDED Requirements

### Requirement: Superseded lifecycle event

The system SHALL add a `Superseded` event type to the `AnchorLifecycleEvent` sealed hierarchy. The event SHALL be published when an anchor is superseded by another anchor.

The `Superseded` event SHALL include:
- `predecessorId` (String) -- the ID of the anchor that was replaced
- `successorId` (String) -- the ID of the anchor that replaced it
- `reason` (String) -- the supersession reason (from `SupersessionReason` enum)
- `occurredAt` (Instant) -- the instant the supersession occurred
- `contextId` (String) -- inherited from `AnchorLifecycleEvent`

Event publishing SHALL be gated by `anchorConfig.lifecycleEventsEnabled()`, consistent with all other lifecycle events.

#### Scenario: Superseded event published on conflict replacement

- **GIVEN** conflict resolution returns REPLACE for anchor "A1" with incoming proposition promoted as "A2"
- **WHEN** the engine processes the replacement
- **THEN** a `Superseded` event SHALL be published with `predecessorId = "A1"`, `successorId = "A2"`, `reason = "CONFLICT_REPLACEMENT"`, and `occurredAt` set to the current instant

#### Scenario: Superseded event contains correct predecessor/successor IDs

- **GIVEN** anchor "A1" at PROVISIONAL authority is being replaced by anchor "A2"
- **WHEN** the supersession is recorded
- **THEN** the `Superseded` event SHALL have `predecessorId = "A1"` (the replaced anchor) and `successorId = "A2"` (the replacing anchor)

#### Scenario: Superseded event not published when events disabled

- **GIVEN** `anchorConfig.lifecycleEventsEnabled() = false`
- **WHEN** a supersession occurs
- **THEN** no `Superseded` event SHALL be published
