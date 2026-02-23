# observability

**Status**: MODIFIED capability (added requirements)
**Change**: retrieval-quality-gate-toolishrag

---

## ADDED Requirements

### Requirement: Retrieval quality gate OTEL span attributes

Each assembly span/observation SHALL include the following retrieval-specific OTEL span attributes, set as low-cardinality key-value pairs via the Micrometer Observation API:

| Attribute | Type | Description |
|-----------|------|-------------|
| `retrieval.mode` | String | The configured `RetrievalMode` value (`BULK`, `TOOL`, or `HYBRID`) |
| `retrieval.baseline_count` | int | Number of anchors included in the baseline system prompt injection |
| `retrieval.tool_call_count` | int | Number of `retrieveAnchors` tool invocations during the turn |
| `retrieval.avg_relevance_score` | double | Mean heuristic relevance score of all anchors included in the baseline injection. SHALL be 0.0 when no anchors are injected. |
| `retrieval.filtered_count` | int | Number of anchors excluded by the quality threshold filter (scoring below `min-relevance` and not CANON) |

These attributes SHALL be set on the assembly span when the anchor context is assembled for injection, regardless of the configured retrieval mode.

#### Scenario: HYBRID mode reports baseline count and tool calls

- **GIVEN** `dice-anchors.retrieval.mode` is `HYBRID`
- **AND** baseline injection includes 7 anchors (2 CANON + 5 by relevance)
- **AND** the LLM invokes `retrieveAnchors` twice during the turn
- **AND** 3 anchors were excluded by the quality threshold
- **AND** the average heuristic score of the 7 baseline anchors is 0.65
- **WHEN** the assembly span is recorded
- **THEN** the span SHALL include:
  - `retrieval.mode = "HYBRID"`
  - `retrieval.baseline_count = 7`
  - `retrieval.tool_call_count = 2`
  - `retrieval.avg_relevance_score = 0.65`
  - `retrieval.filtered_count = 3`

#### Scenario: BULK mode reports full count with no tool calls

- **GIVEN** `dice-anchors.retrieval.mode` is `BULK`
- **AND** 12 active anchors are injected into the system prompt
- **AND** no quality threshold filtering is applied (min-relevance = 0.0)
- **WHEN** the assembly span is recorded
- **THEN** the span SHALL include:
  - `retrieval.mode = "BULK"`
  - `retrieval.baseline_count = 12`
  - `retrieval.tool_call_count = 0`
  - `retrieval.avg_relevance_score = 0.0` (no heuristic scoring in BULK mode)
  - `retrieval.filtered_count = 0`

#### Scenario: TOOL mode reports zero baseline with tool calls

- **GIVEN** `dice-anchors.retrieval.mode` is `TOOL`
- **AND** no anchors are injected into the system prompt
- **AND** the LLM invokes `retrieveAnchors` 3 times during the turn
- **WHEN** the assembly span is recorded
- **THEN** the span SHALL include:
  - `retrieval.mode = "TOOL"`
  - `retrieval.baseline_count = 0`
  - `retrieval.tool_call_count = 3`
  - `retrieval.avg_relevance_score = 0.0`
  - `retrieval.filtered_count = 0`
