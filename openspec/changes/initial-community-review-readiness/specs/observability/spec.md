## MODIFIED Requirements

### Requirement: Simulation turn span includes invariant summary

Simulation turn spans SHALL include invariant and readiness telemetry required for external review traceability. At minimum, spans SHALL capture invariant pass/fail summary, degraded-decision counters, and evidence grade context when available.

#### Scenario: Turn span emits invariant and degraded counters
- **GIVEN** a simulation turn with one degraded decision
- **WHEN** the turn span is emitted
- **THEN** the span SHALL include invariant summary and degraded-decision counters

#### Scenario: Turn span includes execution mode context
- **GIVEN** a turn executed under a benchmark condition
- **WHEN** the turn span is emitted
- **THEN** the span SHALL include execution mode context for run interpretation

## Invariants

- **OBS1**: Observability for claim-grade evidence SHALL expose enough metadata to diagnose provenance and degraded behavior.
