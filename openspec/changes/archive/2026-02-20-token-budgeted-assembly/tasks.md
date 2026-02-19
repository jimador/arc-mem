# Implementation Tasks

## 1. Token Counter SPI

- [x] 1.1 Create `TokenCounter` interface in `assembly/` package
  - Single method: `int estimate(String text)`
- [x] 1.2 Create `CharHeuristicTokenCounter` implementing `TokenCounter`
  - Uses `text.length() / 4` (matching existing codebase heuristic)
- [x] 1.3 Register `CharHeuristicTokenCounter` as Spring bean with `@ConditionalOnMissingBean`

## 2. Budget Enforcement

- [x] 2.1 Create `BudgetResult` record in `assembly/`
  - Fields: included (List<Anchor>), excluded (List<Anchor>), estimatedTokens (int), budgetExceeded (boolean)
- [x] 2.2 Create `PromptBudgetEnforcer` class in `assembly/`
  - [x] 2.2.1 Method: `enforce(List<Anchor> anchors, int tokenBudget, TokenCounter counter, CompliancePolicy policy)`
  - [x] 2.2.2 Estimate preamble + verification protocol overhead
  - [x] 2.2.3 Always include CANON anchors
  - [x] 2.2.4 Drop anchors in order: PROVISIONAL first, then UNRELIABLE, then RELIABLE
  - [x] 2.2.5 Return `BudgetResult` with included/excluded lists
- [x] 2.3 Add logging for budget enforcement decisions

## 3. AnchorsLlmReference Integration

- [x] 3.1 Add `tokenBudget` (int) and `tokenCounter` (TokenCounter) to constructor
- [x] 3.2 In `getContent()`, apply budget enforcement after `limit(budget)` and before grouping
- [x] 3.3 Store `lastBudgetResult` for telemetry access
- [x] 3.4 When `tokenBudget <= 0`, skip enforcement entirely (backward compatible)

## 4. Configuration

- [x] 4.1 Add `AssemblyConfig` record to `DiceAnchorsProperties`
  - Field: `promptTokenBudget` (int, @DefaultValue("0"))
- [x] 4.2 Add `@NestedConfigurationProperty AssemblyConfig assembly` to `DiceAnchorsProperties`
- [x] 4.3 Add `assembly.prompt-token-budget: 0` to `application.yml`
- [x] 4.4 Wire `TokenCounter` bean in assembly configuration

## 5. ContextTrace Extension

- [x] 5.1 Add `budgetApplied` (boolean) and `anchorsExcluded` (int) fields to `ContextTrace`
- [x] 5.2 Add convenience constructor defaulting new fields to false/0
- [x] 5.3 Update `SimulationTurnExecutor` to populate budget fields in trace

## 6. SimulationTurnExecutor Integration

- [x] 6.1 Accept `TokenCounter` via constructor injection
- [x] 6.2 Pass `tokenBudget` and `tokenCounter` when creating `AnchorsLlmReference`
- [x] 6.3 Read budget from properties (or UI override when available)

## 7. SimulationView UI Control

- [x] 7.1 Add `IntegerField` for token budget in scenario settings panel
- [x] 7.2 Label: "Token Budget (0=off)", range 0-5000, default 0
- [x] 7.3 Pass budget value through to `SimulationService` -> `SimulationTurnExecutor`
- [x] 7.4 Store budget in simulation run metadata for tracing

## 8. ChatActions Integration

- [x] 8.1 Pass budget config to `AnchorsLlmReference` in chat flow
- [x] 8.2 When budget is 0, pass 0 (disabled) - no change to current behavior

## 9. Testing

- [x] 9.1 Unit tests for `CharHeuristicTokenCounter`
  - [x] 9.1.1 Test estimate with various string lengths
  - [x] 9.1.2 Test null/empty input returns 0
- [x] 9.2 Unit tests for `PromptBudgetEnforcer`
  - [x] 9.2.1 Test budget not exceeded - all anchors included
  - [x] 9.2.2 Test PROVISIONAL dropped first when over budget
  - [x] 9.2.3 Test CANON never dropped even when over budget
  - [x] 9.2.4 Test empty anchor list
  - [x] 9.2.5 Test budget=0 returns all anchors
- [x] 9.3 Unit tests for `AnchorsLlmReference` budget integration
  - [x] 9.3.1 Test getContent() with budget=0 matches existing behavior
  - [x] 9.3.2 Test getContent() with budget truncates lower-authority anchors
- [x] 9.4 Update existing tests for constructor changes
  - [x] 9.4.1 Update `PromptOrderingContractTest` for new AnchorsLlmReference constructor
  - [x] 9.4.2 Update `ChatActionsTest` if constructor changes
  - [x] 9.4.3 Update `DriftEvaluationTest` for SimulationTurnExecutor constructor change

## 10. Verification

- [x] 10.1 Run full test suite: `./mvnw.cmd test`
- [x] 10.2 Build without tests: `./mvnw.cmd clean compile -DskipTests`
- [x] 10.3 Manual smoke test: Start app, verify no errors on startup
- [x] 10.4 Verify budget enforcement in simulation with token budget set to 500

## 11. Documentation & Cleanup

- [x] 11.1 Add Javadoc to `TokenCounter`, `PromptBudgetEnforcer`, `BudgetResult`
- [x] 11.2 Update CLAUDE.md Key Files table
- [x] 11.3 Verify no debug logging left in code
- [x] 11.4 Code style check per CLAUDE.md
