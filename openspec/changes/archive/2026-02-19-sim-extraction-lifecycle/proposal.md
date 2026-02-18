## Why

Simulation currently only tests anchor *injection resistance* — pre-seeded anchors are injected and we measure drift. But the full DICE lifecycle (extraction → dedup → conflict check → trust evaluation → promotion → reinforcement) never runs during simulation. For demoing to DICE and Embabel maintainers, this is the critical gap: they want to see propositions being extracted from DM responses in real-time, evaluated through the full pipeline, and promoted to anchors that then resist adversarial drift. Without this, the demo shows "anchors work" but not "DICE + Anchors work together."

## What Changes

- Add `extractionEnabled` flag to scenario YAML (opt-in, default false for existing scenarios)
- When enabled, `SimulationTurnExecutor` runs DICE extraction on DM responses after each turn
- Extracted propositions flow through `AnchorPromoter.evaluateAndPromote()` — the full pipeline
- New anchors appear in the timeline alongside seed anchors (visible differentiation: seeded vs extracted)
- Add 2-3 new scenarios that showcase extraction lifecycle:
  - "extraction-baseline": Establish facts through conversation, watch them become anchors
  - "extraction-under-attack": Extract facts, then attack them — shows full lifecycle under pressure
- ContextTrace extended to include extraction metadata (propositions extracted, promoted count)

## Capabilities

### New Capabilities
- `sim-extraction-lifecycle`: DICE proposition extraction during simulation turns with full promotion pipeline

### Modified Capabilities
- `simulation`: Turn execution now optionally includes extraction + promotion after DM response

## Impact

- **Files**: `SimulationTurnExecutor.java` (add extraction call), `SimulationScenario.java` (add extractionEnabled), `ContextTrace.java` (add extraction metadata), new scenario YAML files, `ContextInspectorPanel.java` (show extraction data)
- **APIs**: `executeTurnFull()` gains optional extraction step; backward compatible via flag
- **Config**: Scenario-level `extractionEnabled: true` flag
- **Dependencies**: Requires `ConversationPropositionExtraction` logic (or equivalent) callable from sim context
- **Value**: THE demo moment — watch DICE extract facts, promote to anchors, then resist drift

## Constitutional Alignment

- RFC 2119 keywords: Extraction MUST use the same pipeline as chat (AnchorPromoter); scenarios MUST be backward-compatible
- Scenario isolation: Extraction uses sim contextId (sim-{uuid}), cleaned up after
- Single-module Maven project: Changes in sim/engine and sim/views packages
