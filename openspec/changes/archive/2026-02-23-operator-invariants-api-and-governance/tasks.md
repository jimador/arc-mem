## 1. Invariant Rule Model (D1)

- [x] 1.1 Create `InvariantStrength` enum with `MUST` and `SHOULD` values in `anchor/` package
- [x] 1.2 Create `ProposedAction` enum with `ARCHIVE`, `EVICT`, `DEMOTE`, `AUTHORITY_CHANGE` values in `anchor/` package
- [x] 1.3 Create sealed interface `InvariantRule` with four record implementations: `AuthorityFloor`, `EvictionImmunity`, `MinAuthorityCount`, `ArchiveProhibition` — each with `id()`, `strength()`, `contextId()` and type-specific fields
- [x] 1.4 Create `InvariantViolationData` record for passing violation details between evaluator and event factory: `ruleId`, `strength`, `blockedAction`, `constraintDescription`, `anchorId`

## 2. Invariant Evaluator Service (D2)

- [x] 2.1 Create `InvariantEvaluation` result record with `violations` list, `checkedCount`, `hasBlockingViolation()`, `hasWarnings()` methods
- [x] 2.2 Create `InvariantRuleProvider` service: loads rules from `DiceAnchorsProperties`, supports `registerForContext()` and `deregisterForContext()` for scenario-scoped rules
- [x] 2.3 Create `InvariantEvaluator` service: `evaluate(contextId, ProposedAction, List<Anchor>, Anchor targetAnchor)` — filters rules by context scope, evaluates each against proposed action, returns `InvariantEvaluation`
- [x] 2.4 Implement evaluation logic for each rule type: `AuthorityFloor` checks demotion below floor, `EvictionImmunity` blocks eviction, `MinAuthorityCount` checks context anchor counts, `ArchiveProhibition` blocks archive

## 3. YAML Configuration (D4)

- [x] 3.1 Add `InvariantConfig` and `InvariantRuleDefinition` inner records to `DiceAnchorsProperties`
- [x] 3.2 Add `invariants` field to `DiceAnchorsProperties` constructor
- [x] 3.3 Implement type mapping in `InvariantRuleProvider`: YAML `type` string to `InvariantRule` sealed subtypes
- [x] 3.4 Extend `ScenarioLoader` to parse `invariants` section from scenario YAML and register with `InvariantRuleProvider`
- [x] 3.5 Update all existing `DiceAnchorsProperties` constructor call sites for the new `invariants` field

## 4. AnchorEngine Enforcement Hooks (D2, D3)

- [x] 4.1 Add `InvariantEvaluator` constructor parameter to `AnchorEngine`
- [x] 4.2 Add invariant enforcement in `archive()`: evaluate before `repository.archiveAnchor()`, block on MUST, warn on SHOULD
- [x] 4.3 Add invariant enforcement in `promote()` eviction path: filter eviction candidates by `EvictionImmunity`, check `MinAuthorityCount` before evicting
- [x] 4.4 Add invariant enforcement in `demote()`: evaluate `AuthorityFloor` and `MinAuthorityCount` before demotion
- [x] 4.5 Update `supersede()` to route through invariant-aware `archive()` path

## 5. InvariantViolation Lifecycle Event (D5)

- [x] 5.1 Add `InvariantViolation` inner class to `AnchorLifecycleEvent` sealed hierarchy with fields: ruleId, strength, blockedAction, constraintDescription, anchorId
- [x] 5.2 Update `permits` clause on `AnchorLifecycleEvent` to include `InvariantViolation`
- [x] 5.3 Add `invariantViolation()` static factory methods (full params and convenience overload accepting `InvariantViolationData`)
- [x] 5.4 Publish `InvariantViolation` events from enforcement hooks in `AnchorEngine`
- [x] 5.5 Add `InvariantViolation` handling in `AnchorLifecycleListener` with WARN-level logging

## 6. OTEL Span Attributes (D9)

- [x] 6.1 Add OTEL span attribute writes after each invariant evaluation in `AnchorEngine`: `invariant.checked_count`, `invariant.violated_count`, `invariant.blocked_action`, `invariant.must_violations`, `invariant.should_violations`

## 7. CanonizationGate Neo4j Persistence (D6)

- [x] 7.1 Create `CanonizationRequestNode` entity or add Cypher queries to `AnchorRepository` for CRUD operations on `CanonizationRequest` nodes
- [x] 7.2 Add audit trail fields to `CanonizationRequest` record: `resolvedAt`, `resolvedBy`, `resolutionNote`
- [x] 7.3 Replace `ConcurrentHashMap` in `CanonizationGate` with Neo4j repository calls
- [x] 7.4 Add `CANONIZATION_REQUEST_FOR` relationship creation in request persistence query
- [x] 7.5 Add context cleanup: mark PENDING requests as STALE when simulation context is torn down

## 8. Chat Seed Anchors (D7)

- [x] 8.1 Add `ChatSeedConfig` and `ChatSeedAnchor` inner records to `DiceAnchorsProperties`
- [x] 8.2 Add `chatSeed` field to `DiceAnchorsProperties` constructor and update all call sites
- [x] 8.3 Create `ChatContextInitializer` service: reads seed config, creates propositions, promotes with specified authority/rank/pinned — idempotent (skips existing matches)
- [x] 8.4 Wire `ChatContextInitializer` into `ChatActions` context setup flow
- [x] 8.5 CANON seed anchors bypass canonization gate (direct `setAuthority()`)

## 9. Invariant Inspector UI (D8)

- [x] 9.1 Add "Invariants" tab to `ContextInspectorPanel` TabSheet
- [x] 9.2 Implement active rules display: rule ID, type badge, strength badge (red MUST / amber SHOULD), scope, constraint description
- [x] 9.3 Implement violation history display: chronological list with timestamp, rule ID, action, anchor ID, blocked/warned badge
- [x] 9.4 Add summary badge to tab header: red for MUST violations, amber for SHOULD-only, green when clean
- [x] 9.5 Wire invariant data into `SimulationProgress` or `ContextTrace` so UI receives active rules and violations per turn

## 10. Unit Tests

- [x] 10.1 Test `InvariantRule` sealed subtypes: construction, field access, pattern matching
- [x] 10.2 Test `InvariantEvaluator`: each rule type evaluation, context scoping (global vs specific), MUST vs SHOULD separation, mixed rule evaluation
- [x] 10.3 Test `InvariantRuleProvider`: loading from config, context registration/deregistration, type mapping from YAML strings
- [x] 10.4 Test `AnchorEngine` enforcement hooks: archive blocked by MUST, archive warned by SHOULD, eviction skips immune candidates, demotion blocked by authority floor, MinAuthorityCount blocks when count would drop below minimum
- [x] 10.5 Test `InvariantViolation` event: factory methods, field access, sealed hierarchy membership
- [x] 10.6 Test `CanonizationGate` Neo4j persistence: create request, approve, reject, stale on cleanup, audit trail fields populated
- [x] 10.7 Test `ChatContextInitializer`: seeds created on first init, idempotent on second init, CANON bypasses gate, disabled config skips seeding
- [x] 10.8 Test OTEL span attributes: attributes set on evaluation, not set when no invariants defined

## 11. Existing Test Updates

- [x] 11.1 Update `AnchorEngineTest` for new `InvariantEvaluator` constructor parameter (mock or no-op evaluator)
- [x] 11.2 Update `AnchorPromoterTest` if promotion path changes affect mock expectations
- [x] 11.3 Update any `CanonizationGate` tests for Neo4j persistence change
- [x] 11.4 Update `DiceAnchorsProperties` construction in all test classes for new fields

## 12. Verification

- [x] 12.1 Run full test suite — all tests pass with zero failures
- [x] 12.2 Compile check — `./mvnw.cmd clean compile -DskipTests` succeeds

## 13. UI Regression Test (Playwright via Docker MCP Gateway)

- [x] 13.1 Navigate Playwright browser to SimulationView (`http://host.docker.internal:8089/`) — take snapshot, verify page renders with scenario selector and start button present
- [x] 13.2 Start a simulation via Playwright with a scenario that has invariant rules — click start, wait for turns, verify Invariants tab appears in Context Inspector with active rules and status badges
- [x] 13.3 Trigger an invariant violation during simulation — verify violation appears in Invariants tab with correct styling (red for MUST, amber for SHOULD) via snapshot inspection
- [x] 13.4 Navigate Playwright browser to ChatView (`http://host.docker.internal:8089/chat`) — take snapshot, verify page renders with chat input, verify chat seed anchors appear if configured
- [x] 13.5 Check Playwright browser console messages for errors — verify no JS errors or broken component rendering in either view after invariant and canonization gate changes
