## ADDED Requirements

### Requirement: Superseded lifecycle event

The system SHALL add a `Superseded` event type to the `ContextUnitLifecycleEvent` sealed hierarchy. The event SHALL be published when an context unit is superseded by another context unit.

The `Superseded` event SHALL include:
- `predecessorId` (String) -- the ID of the context unit that was replaced
- `successorId` (String) -- the ID of the context unit that replaced it
- `reason` (String) -- the supersession reason (from `SupersessionReason` enum)
- `occurredAt` (Instant) -- the instant the supersession occurred
- `contextId` (String) -- inherited from `ContextUnitLifecycleEvent`

Event publishing SHALL be gated by `unitConfig.lifecycleEventsEnabled()`, consistent with all other lifecycle events.

#### Scenario: Superseded event published on conflict replacement

- **GIVEN** conflict resolution returns REPLACE for context unit "A1" with incoming proposition promoted as "A2"
- **WHEN** the engine processes the replacement
- **THEN** a `Superseded` event SHALL be published with `predecessorId = "A1"`, `successorId = "A2"`, `reason = "CONFLICT_REPLACEMENT"`, and `occurredAt` set to the current instant

#### Scenario: Superseded event contains correct predecessor/successor IDs

- **GIVEN** context unit "A1" at PROVISIONAL authority is being replaced by context unit "A2"
- **WHEN** the supersession is recorded
- **THEN** the `Superseded` event SHALL have `predecessorId = "A1"` (the replaced context unit) and `successorId = "A2"` (the replacing context unit)

#### Scenario: Superseded event not published when events disabled

- **GIVEN** `unitConfig.lifecycleEventsEnabled() = false`
- **WHEN** a supersession occurs
- **THEN** no `Superseded` event SHALL be published
