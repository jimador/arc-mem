## ADDED Requirements

### Requirement: OTEL span attributes for supersession events

When a REPLACE conflict resolution outcome creates a supersession, the system SHALL record the following OTEL span attributes on the `conflict.resolution` observation span:

- `supersession.reason` (String) -- the supersession reason (e.g., `"CONFLICT_REPLACEMENT"`)
- `supersession.predecessor_id` (String) -- the anchor ID of the replaced (predecessor) anchor
- `supersession.successor_id` (String) -- the anchor ID of the replacing (successor) anchor

These attributes SHALL be set as low-cardinality key-value pairs via the Micrometer Observation API, on the same `conflict.resolution` span that already records `conflict.existing_authority`, `conflict.incoming_confidence_band`, `conflict.existing_tier`, and `conflict.resolution`.

When the conflict resolution outcome is not REPLACE (i.e., KEEP_EXISTING, COEXIST, or DEMOTE_EXISTING), no supersession span attributes SHALL be set.

#### Scenario: REPLACE conflict resolution span includes supersession attributes

- **GIVEN** an incoming proposition conflicts with anchor "A1" at PROVISIONAL authority
- **AND** conflict resolution returns REPLACE
- **WHEN** the engine processes the resolution, promoting the incoming proposition as "A2"
- **THEN** the `conflict.resolution` span SHALL include `supersession.reason = "CONFLICT_REPLACEMENT"`, `supersession.predecessor_id = "A1"`, `supersession.successor_id = "A2"`
- **AND** the span SHALL also include `conflict.resolution = "REPLACE"`

#### Scenario: Non-REPLACE resolution has no supersession attributes

- **GIVEN** an incoming proposition conflicts with anchor "A1" at RELIABLE authority
- **AND** conflict resolution returns DEMOTE_EXISTING
- **WHEN** the `conflict.resolution` span is recorded
- **THEN** the span SHALL NOT include any `supersession.*` attributes
- **AND** the span SHALL include `conflict.resolution = "DEMOTE_EXISTING"`
