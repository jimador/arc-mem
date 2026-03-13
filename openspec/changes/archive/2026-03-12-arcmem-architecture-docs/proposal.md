## Why

The existing documentation covers architecture at a high level but lacks visual data-flow diagrams and end-to-end execution traces. A developer reading `architecture.md` today cannot trace how a user message becomes memory units, how conflicts are detected and resolved, or how authority transitions propagate through the system. Mermaid diagrams and detailed flow descriptions will close this gap, making the system auditable from docs alone.

## What Changes

- Add comprehensive Mermaid diagrams to `docs/architecture.md` covering all major subsystems
- Create a new `docs/data-flows.md` with end-to-end execution traces for key scenarios
- Update `docs/promotion-revision-supersession.md` with visual state machines and example data flows
- Add conflict detection pipeline diagrams showing strategy composition and fallback paths
- Add trust pipeline scoring diagrams with signal weighting and zone mapping
- Add maintenance strategy state machines (REACTIVE vs PROACTIVE vs HYBRID lifecycle)
- Add context assembly pipeline diagram showing retrieval modes, budget enforcement, and compliance
- Document example promotion and demotion data flows with concrete state transitions

## Capabilities

### New Capabilities

- `architecture-diagrams`: Mermaid diagrams for all major subsystems — memory lifecycle state machine, conflict detection pipeline, trust pipeline scoring, maintenance strategy state machines, context assembly pipeline, module dependency graph, and simulation harness architecture

### Modified Capabilities

_No spec-level requirement changes. This change adds documentation artifacts only._

## Impact

- **Files modified**: `docs/architecture.md`, `docs/promotion-revision-supersession.md`
- **Files created**: `docs/data-flows.md`
- **No code changes**: Documentation-only change
- **No API changes**: No behavioral modifications
- **No dependency changes**: Mermaid renders natively in GitHub/GitLab markdown

## Constitutional Alignment

- **Article V (ARC-Mem Invariants)**: All diagrams MUST accurately reflect invariants A1–A4 (capacity bounds, activation score range, explicit promotion, authority rules)
- **Article II (Neo4j Sole Persistence)**: Diagrams MUST show Neo4j as the only persistence layer
- **Article VI (Simulation Isolation)**: Diagrams showing sim execution MUST depict `sim-{uuid}` context isolation
- **Article I (RFC 2119)**: Spec artifacts use RFC 2119 keywords where applicable
