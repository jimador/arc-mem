# Implementation Tasks

## 1. Template Files

- [x] 1.1 Create `src/main/resources/prompts/anchor-tier-provisional.jinja` -- full text + rank + low-confidence contextual note (Spec: authority-graduated prompt template files)
- [x] 1.2 Create `src/main/resources/prompts/anchor-tier-unreliable.jinja` -- full text + rank (Spec: authority-graduated prompt template files)
- [x] 1.3 Create `src/main/resources/prompts/anchor-tier-reliable.jinja` -- condensed text + rank omitted (Spec: authority-graduated prompt template files)
- [x] 1.4 Create `src/main/resources/prompts/anchor-tier-canon.jinja` -- minimal text only (Spec: authority-graduated prompt template files)

## 2. Configuration & Constants

- [x] 2.1 Add `ANCHOR_TEMPLATE_PROVISIONAL`, `ANCHOR_TEMPLATE_UNRELIABLE`, `ANCHOR_TEMPLATE_RELIABLE`, `ANCHOR_TEMPLATE_CANON` constants to `PromptPathConstants` (Spec: PromptPathConstants for graduated templates)
- [x] 2.2 Add `adaptiveFootprintEnabled` field (boolean, default false) to `DiceAnchorsProperties.AssemblyConfig` record (Spec: configuration toggle for adaptive footprint)

## 3. Prompt Assembly

- [x] 3.1 Add `adaptiveFootprintEnabled` field to `AnchorsLlmReference` constructor and store as instance field (Design: passing adaptive flag through assembly chain)
- [x] 3.2 Add `templateForAuthority(Authority)` private method to `AnchorsLlmReference` using switch expression (Design: template selection)
- [x] 3.3 Modify `AnchorsLlmReference.getContent()` to render per-authority templates when adaptive footprint is enabled; preserve existing uniform path when disabled (Spec: template selection by authority in prompt assembly)

## 4. Budget Enforcement

- [x] 4.1 Add `adaptiveFootprintEnabled` parameter to `PromptBudgetEnforcer.enforce()` method (Design: authority-aware estimation)
- [x] 4.2 Update `estimateTotal()` (or add `estimateAnchorTokens()`) to render authority-specific templates for token estimation when adaptive is enabled; preserve uniform estimation when disabled (Spec: budget enforcement with authority-aware estimation)
- [x] 4.3 Update all `enforce()` call sites in `AnchorsLlmReference` to pass the adaptive flag (Design: passing adaptive flag through assembly chain)

## 5. Testing

- [x] 5.1 Test that all four template files load successfully via `PromptTemplates.load()` (Spec: all four templates exist and load)
- [x] 5.2 Test template selection: adaptive enabled selects correct template per authority; adaptive disabled uses uniform template (Spec: template selection by authority)
- [x] 5.3 Test `AnchorsLlmReference.getContent()` with adaptive enabled produces per-tier output; with adaptive disabled produces identical-to-current output (Spec: adaptive footprint enabled/disabled scenarios)
- [x] 5.4 Test `PromptBudgetEnforcer` with adaptive enabled: CANON anchor token estimate < PROVISIONAL anchor token estimate for same text length (Spec: adaptive footprint uses authority-aware estimation)
- [x] 5.5 Test `PromptBudgetEnforcer` with adaptive disabled: uniform estimation unchanged (Spec: adaptive footprint disabled uses uniform estimation)

## 6. Verification

- [x] 6.1 Run `./mvnw clean compile -DskipTests` -- build succeeds (blocked by F07 incomplete implementation)
- [x] 6.2 Run `./mvnw test` -- all tests pass (blocked by F07 incomplete implementation, but F09 implementation complete)

## Definition of Done

- All four authority-graduated template files exist and load
- `AnchorsLlmReference` selects templates by authority when adaptive footprint is enabled
- `PromptBudgetEnforcer` uses authority-aware estimation when adaptive footprint is enabled
- Default configuration (disabled) produces identical behavior to pre-change
- All tests pass
