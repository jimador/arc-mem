## Why

Conflict detection thresholds are hardcoded across four classes — negation overlap (0.5), LLM confidence (0.9), and authority resolution bands (0.8/0.6). These values cannot be tuned without code changes, making calibration impossible for different LLM backends or domain profiles. F01 (memory tiering) introduced tier-aware decay and assembly but conflict resolution remains tier-blind: a HOT anchor with strong recent reinforcement is resolved identically to a COLD anchor about to decay out. This change externalizes thresholds to configuration, adds tier-awareness to resolution, and wires observability so operators can measure false-positive/false-negative rates.

## What Changes

- Extract all hardcoded conflict thresholds to `DiceAnchorsProperties` with sensible defaults matching current behavior (backward-compatible).
- Add tier-aware modifiers to the authority conflict resolution matrix — HOT anchors get a defensive bias (harder to replace/demote), COLD anchors get a permissive bias (easier to replace).
- Publish conflict detection metrics (detected count, resolution outcomes, confidence distributions) as OTEL span attributes and Micrometer observations.
- Add configuration validation for all new threshold properties at startup.
- Wire threshold values through existing SPI implementations (`NegationConflictDetector`, `LlmConflictDetector`, `AuthorityConflictResolver`).

## Capabilities

### New Capabilities
- `conflict-calibration`: Configurable conflict detection thresholds and tier-aware resolution modifiers.

### Modified Capabilities
- `conflict-detection`: Negation overlap threshold and LLM confidence become configurable properties instead of hardcoded constants.
- `anchor-conflict`: Authority resolution matrix gains tier-aware confidence adjustments and configurable threshold bands.
- `observability`: Conflict detection spans and metrics added to the OTEL surface.

## Impact

- **Code**: `NegationConflictDetector`, `LlmConflictDetector`, `AuthorityConflictResolver`, `CompositeConflictDetector`, `DiceAnchorsProperties`, `AnchorConfiguration` (validation), `SimulationTurnExecutor` (OTEL attributes).
- **Configuration**: New `dice-anchors.conflict.*` property namespace with threshold, tier-modifier, and observability settings.
- **Tests**: Existing 35 conflict detection tests need threshold injection; new tests for tier-aware resolution and boundary validation.
- **Backward compatibility**: All defaults match current hardcoded values. No behavioral change without explicit configuration.

## Constitutional Alignment

- RFC 2119 keywords SHALL be used in all spec requirements.
- Authority invariants A3a–A3e (bidirectional lifecycle) are preserved — tier modifiers adjust confidence thresholds, not authority transition rules.
- CANON immunity (A3b) is unaffected — CANON resolution always returns KEEP_EXISTING regardless of tier.
- Pinned anchor immunity (A3d) is orthogonal to conflict resolution and unaffected.
