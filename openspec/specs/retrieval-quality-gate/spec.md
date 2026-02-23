## Requirements

### Requirement: Retrieval mode configuration

The system SHALL provide a `RetrievalMode` enum with three values: `BULK`, `TOOL`, and `HYBRID`.

The retrieval mode SHALL be configured via `dice-anchors.retrieval.mode` with a default value of `HYBRID`.

- **BULK**: Current bulk injection behavior. All active anchors (up to budget) are injected into the system prompt. No quality gate is applied. No retrieval tool is registered.
- **TOOL**: The LLM retrieves anchors exclusively via the `retrieveAnchors` tool. No anchors SHALL be injected into the system prompt baseline.
- **HYBRID**: A baseline set of anchors (all CANON anchors plus top-N non-CANON anchors ranked by composite heuristic score) SHALL be injected into the system prompt. The retrieval tool SHALL also be available for the LLM to fetch additional anchors on demand.

#### Scenario: BULK mode backward compatible

- **GIVEN** `dice-anchors.retrieval.mode` is set to `BULK`
- **WHEN** the assembly pipeline runs
- **THEN** all active anchors up to budget are injected into the system prompt
- **AND** no retrieval tool is registered
- **AND** behavior is identical to the pre-change assembly pipeline

#### Scenario: HYBRID mode reduces baseline injection

- **GIVEN** `dice-anchors.retrieval.mode` is set to `HYBRID`
- **AND** there are 15 active anchors, 2 of which are CANON
- **AND** `dice-anchors.retrieval.baseline-top-k` is 5
- **WHEN** the assembly pipeline runs
- **THEN** the 2 CANON anchors plus the top 5 non-CANON anchors by heuristic score are injected (7 total)
- **AND** the retrieval tool is available for the remaining 8 anchors

#### Scenario: TOOL mode produces no baseline injection

- **GIVEN** `dice-anchors.retrieval.mode` is set to `TOOL`
- **WHEN** the assembly pipeline runs
- **THEN** no anchors are injected into the system prompt
- **AND** the retrieval tool is the sole mechanism for anchor access

---

### Requirement: Relevance scoring

The system SHALL compute a composite relevance score for anchor selection.

**Baseline selection (HYBRID mode):** A heuristic score SHALL be computed without an LLM call using a weighted-sum formula:

```
heuristic_score = (authority_weight * authority_value) + (tier_weight * tier_value) + (confidence_weight * confidence)
```

Where the configurable weights default to `authority_weight=0.4`, `tier_weight=0.3`, `confidence_weight=0.3` (must sum to 1.0).

Authority values:
- CANON = 1.0
- RELIABLE = 0.8
- UNRELIABLE = 0.5
- PROVISIONAL = 0.3

Tier values:
- HOT = 1.0
- WARM = 0.7
- COLD = 0.4

The `confidence` value SHALL be the anchor's existing DICE confidence score (0.0--1.0).

**Tool retrieval:** When the LLM invokes the retrieval tool, relevance SHALL be determined by combining an LLM-judged relevance score (0.0--1.0) with the heuristic score using a 60/40 blend: `(0.6 * llm_score) + (0.4 * heuristic_score)`. The scoring weights for the heuristic component SHALL be configurable via `dice-anchors.retrieval.scoring.*`.

#### Scenario: High-authority HOT anchor scores highest

- **GIVEN** a RELIABLE anchor with tier HOT and confidence 0.9
- **AND** default scoring weights (authority=0.4, tier=0.3, confidence=0.3)
- **WHEN** the heuristic score is computed
- **THEN** the score is (0.4 * 0.8) + (0.3 * 1.0) + (0.3 * 0.9) = 0.89

#### Scenario: COLD PROVISIONAL anchor scores lowest

- **GIVEN** a PROVISIONAL anchor with tier COLD and confidence 0.3
- **AND** default scoring weights (authority=0.4, tier=0.3, confidence=0.3)
- **WHEN** the heuristic score is computed
- **THEN** the score is (0.4 * 0.3) + (0.3 * 0.4) + (0.3 * 0.3) = 0.33

---

### Requirement: Quality threshold filtering

The system SHALL support a configurable minimum relevance threshold via `dice-anchors.retrieval.min-relevance` with a default value of `0.0`.

Anchors with a heuristic score below the configured threshold SHALL be excluded from retrieval results (both baseline injection and tool retrieval).

CANON anchors SHALL be exempt from quality threshold filtering. CANON anchors MUST always be included regardless of their computed score.

#### Scenario: Threshold filters low-relevance anchors

- **GIVEN** `dice-anchors.retrieval.min-relevance` is set to `0.3`
- **AND** a PROVISIONAL COLD anchor has a heuristic score of 0.036
- **WHEN** the quality gate is applied
- **THEN** the anchor is excluded from the result set

#### Scenario: CANON anchors immune to filtering

- **GIVEN** `dice-anchors.retrieval.min-relevance` is set to `0.5`
- **AND** a CANON anchor has a heuristic score of 0.4 (below threshold)
- **WHEN** the quality gate is applied
- **THEN** the CANON anchor is still included in the result set

---

### Requirement: Anchor retrieval tool

The system SHALL provide an `@LlmTool` method `retrieveAnchors(String query)` exposed via a `@MatryoshkaTools` record.

The tool SHALL search existing active anchors by relevance to the provided query string. Relevance SHALL be determined by the LLM scoring each anchor's text against the query, producing a relevance score between 0.0 and 1.0. This LLM-judged score SHALL be combined with the heuristic score for final ranking.

The tool SHALL return the top-k results (configurable via `dice-anchors.retrieval.tool-top-k`, default 5). Each result SHALL include: anchor ID, anchor text, authority level, memory tier, and computed relevance score.

The retrieval tool SHALL be available in `TOOL` and `HYBRID` modes. The tool MUST NOT be registered in `BULK` mode.

#### Scenario: Tool returns relevant anchors

- **GIVEN** the retrieval mode is `HYBRID`
- **AND** 10 active anchors exist, 3 of which are semantically relevant to "NPC motivations"
- **WHEN** the LLM calls `retrieveAnchors("NPC motivations")`
- **THEN** the tool returns results ranked by combined relevance score
- **AND** the semantically relevant anchors appear at the top of the results

#### Scenario: Tool respects top-k limit

- **GIVEN** `dice-anchors.retrieval.tool-top-k` is set to 3
- **AND** 10 active anchors exist
- **WHEN** the LLM calls `retrieveAnchors("party inventory")`
- **THEN** at most 3 results are returned

#### Scenario: Tool unavailable in BULK mode

- **GIVEN** `dice-anchors.retrieval.mode` is set to `BULK`
- **WHEN** the chat actions are initialized
- **THEN** the `retrieveAnchors` tool is not registered with the LLM

---

### Requirement: Retrieval configuration properties

The system SHALL provide a `RetrievalConfig` record bound to `dice-anchors.retrieval.*` within `DiceAnchorsProperties`.

Configuration properties:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `mode` | `RetrievalMode` | `HYBRID` | Retrieval strategy |
| `min-relevance` | `double` | `0.0` | Minimum heuristic score threshold |
| `baseline-top-k` | `int` | `5` | Max non-CANON anchors in HYBRID baseline |
| `tool-top-k` | `int` | `5` | Max results from retrieval tool |

Nested `ScoringConfig` under `dice-anchors.retrieval.scoring.*`:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `authority-weight` | `double` | `0.4` | Weight of authority factor in heuristic score |
| `tier-weight` | `double` | `0.3` | Weight of tier factor in heuristic score |
| `confidence-weight` | `double` | `0.3` | Weight of confidence factor in heuristic score |

**Validation constraints:**
- `authority-weight + tier-weight + confidence-weight` MUST equal 1.0
- `min-relevance` MUST be in the range [0.0, 1.0]
- `baseline-top-k` MUST be greater than 0
- `tool-top-k` MUST be greater than 0

#### Scenario: Valid configuration accepted

- **GIVEN** `dice-anchors.retrieval.mode=HYBRID`, `min-relevance=0.2`, `baseline-top-k=5`, `tool-top-k=5`
- **AND** scoring weights `authority-weight=0.4`, `tier-weight=0.3`, `confidence-weight=0.3`
- **WHEN** the application starts
- **THEN** the configuration is accepted and the `RetrievalConfig` bean is created

#### Scenario: Invalid scoring weights rejected

- **GIVEN** scoring weights `authority-weight=0.5`, `tier-weight=0.5`, `confidence-weight=0.5` (sum = 1.5)
- **WHEN** the application starts
- **THEN** validation fails with an error indicating weights must sum to 1.0

#### Scenario: Default configuration is backward compatible

- **GIVEN** no `dice-anchors.retrieval.*` properties are configured
- **WHEN** the application starts
- **THEN** defaults are applied: mode=HYBRID, min-relevance=0.0, baseline-top-k=5, tool-top-k=5
- **AND** existing behavior is preserved (min-relevance=0.0 means no anchors are filtered by quality gate)
