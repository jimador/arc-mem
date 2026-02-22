## ADDED Requirements

### Requirement: Conflict detection OTEL span attributes

Each `simulation.turn` span SHALL include the following conflict-related OTEL span attributes:

- `conflict.detected_count` (int) -- total number of conflicts detected during the turn
- `conflict.resolved_count` (int) -- total number of conflicts that were resolved during the turn
- `conflict.resolution_outcomes` (String) -- comma-separated list of resolution types applied during the turn (e.g., `"REPLACE,KEEP_EXISTING,DEMOTE_EXISTING"`)

These attributes SHALL be set as low-cardinality key-value pairs via the Micrometer Observation API. When no conflicts are detected during a turn, `conflict.detected_count` SHALL be `0`, `conflict.resolved_count` SHALL be `0`, and `conflict.resolution_outcomes` SHALL be an empty string.

#### Scenario: Attack turn with conflicts

- **GIVEN** a simulation turn that detects 3 conflicts
- **AND** resolution produces 2 REPLACE and 1 KEEP_EXISTING outcomes
- **WHEN** the `simulation.turn` span is recorded
- **THEN** the span SHALL include `conflict.detected_count = 3`, `conflict.resolved_count = 3`, `conflict.resolution_outcomes = "REPLACE,REPLACE,KEEP_EXISTING"`

#### Scenario: Turn with no conflicts

- **GIVEN** a simulation turn that detects no conflicts
- **WHEN** the `simulation.turn` span is recorded
- **THEN** the span SHALL include `conflict.detected_count = 0`, `conflict.resolved_count = 0`, `conflict.resolution_outcomes = ""`

### Requirement: Conflict resolution observation

A Micrometer observation named `conflict.resolution` SHALL be created around each `AuthorityConflictResolver.resolve()` call. The observation SHALL include the following low-cardinality key-value pairs:

- `conflict.existing_authority` (String) -- the authority level of the existing anchor (e.g., `"RELIABLE"`, `"PROVISIONAL"`)
- `conflict.incoming_confidence_band` (String) -- the confidence band of the incoming proposition: `"LOW"` (< 0.4), `"MEDIUM"` (0.4 -- 0.8), or `"HIGH"` (> 0.8)
- `conflict.existing_tier` (String) -- the tier of the existing anchor (`"HOT"`, `"WARM"`, or `"COLD"`)
- `conflict.resolution` (String) -- the resolution outcome (e.g., `"REPLACE"`, `"KEEP_EXISTING"`, `"DEMOTE_EXISTING"`)

The observation SHALL be stopped after the resolution decision is made, regardless of whether the resolution succeeds or fails.

#### Scenario: High-confidence conflict with HOT anchor observed

- **GIVEN** an incoming proposition with confidence `0.85` conflicts with a HOT RELIABLE anchor
- **WHEN** `AuthorityConflictResolver.resolve()` is called
- **THEN** a `conflict.resolution` observation SHALL be recorded with `conflict.existing_authority = "RELIABLE"`, `conflict.incoming_confidence_band = "HIGH"`, `conflict.existing_tier = "HOT"`, `conflict.resolution = "DEMOTE_EXISTING"`

#### Scenario: Low-confidence conflict observed

- **GIVEN** an incoming proposition with confidence `0.3` conflicts with a WARM UNRELIABLE anchor
- **WHEN** `AuthorityConflictResolver.resolve()` is called
- **THEN** a `conflict.resolution` observation SHALL be recorded with `conflict.existing_authority = "UNRELIABLE"`, `conflict.incoming_confidence_band = "LOW"`, `conflict.existing_tier = "WARM"`, `conflict.resolution = "DEMOTE_EXISTING"`
