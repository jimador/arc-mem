## ADDED Requirements

### Requirement: OTEL span attributes for tier transitions

When a `TierChanged` lifecycle event is published, the system SHALL record OTEL span attributes on the active span (if present):
- `anchor.tier` (String): The new tier value (`HOT`, `WARM`, `COLD`)
- `anchor.tier.previous` (String): The previous tier value
- `anchor.id` (String): The anchor ID

These attributes SHALL be set as low-cardinality key-value pairs via the Micrometer Observation API.

#### Scenario: Tier upgrade span attributes

- **GIVEN** an active OTEL span during anchor reinforcement
- **WHEN** a `TierChanged` event is published with `previousTier = WARM` and `newTier = HOT`
- **THEN** the span SHALL include attributes `anchor.tier = "HOT"` and `anchor.tier.previous = "WARM"`

#### Scenario: No active span

- **GIVEN** no active OTEL span (e.g., background decay job)
- **WHEN** a `TierChanged` event is published
- **THEN** the event SHALL be logged at INFO level but no span attributes SHALL be set

### Requirement: Tier distribution in simulation turn spans

Each `simulation.turn` span SHALL include tier distribution attributes:
- `anchor.tier.hot_count` (int): Number of HOT anchors in the assembled prompt
- `anchor.tier.warm_count` (int): Number of WARM anchors in the assembled prompt
- `anchor.tier.cold_count` (int): Number of COLD anchors in the assembled prompt

#### Scenario: Turn span includes tier counts

- **GIVEN** a simulation turn assembling a prompt with 4 HOT, 8 WARM, and 3 COLD anchors
- **WHEN** the `simulation.turn` span is recorded
- **THEN** the span SHALL include `anchor.tier.hot_count = 4`, `anchor.tier.warm_count = 8`, `anchor.tier.cold_count = 3`
