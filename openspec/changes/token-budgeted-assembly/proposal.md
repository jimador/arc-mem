## Why

Anchor injection can consume unbounded prompt tokens. With 20 anchors at ~50 tokens each plus the RFC 2119 preamble and verification protocol, the anchor block can reach 1200+ tokens — significant for smaller context windows or cost-sensitive demos. The Tor project's anchor engine includes token-budgeted assembly; adopting it here ensures anchor injection never crowds out system instructions or user context, and provides a visible demo control for tuning prompt economics.

## What Changes

- Add `TokenCounter` SPI interface with `CharHeuristicTokenCounter` default (chars/4, matching existing heuristic)
- Add `PromptBudgetEnforcer` that truncates anchors by authority priority when token budget is exceeded
- CANON facts MUST never be truncated; RFC 2119 preamble and verification protocol are mandatory
- Truncation order: PROVISIONAL → UNRELIABLE → RELIABLE (drop entire anchors, lowest-authority first)
- Integrate budget enforcement into `AnchorsLlmReference.getContent()`
- Add `assembly.prompt-token-budget` config property (default 0 = disabled)
- Extend `ContextTrace` with budget metadata (applied, excluded count)
- Add token budget slider/input in SimulationView settings panel for demo configurability

## Capabilities

### New Capabilities
- `prompt-token-budget`: Configurable token budget for anchor prompt assembly with authority-prioritized truncation and UI controls

### Modified Capabilities
_(none — additive only)_

## Impact

- `AnchorsLlmReference` gains optional budget enforcement in `getContent()`
- New `assembly/` classes: `TokenCounter`, `CharHeuristicTokenCounter`, `PromptBudgetEnforcer`
- `DiceAnchorsProperties` extended with `AssemblyConfig` nested record
- `ContextTrace` extended with budget fields
- `SimulationView` gains budget control in settings panel
- `SimulationTurnExecutor` passes budget config to `AnchorsLlmReference`
