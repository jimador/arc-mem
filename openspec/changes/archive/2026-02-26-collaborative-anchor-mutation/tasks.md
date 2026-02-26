## 1. AnchorMutationStrategy SPI

- [x] 1.1 Create `MutationSource` enum (`UI`, `LLM_TOOL`, `CONFLICT_RESOLVER`) in `anchor/` package
- [x] 1.2 Create `MutationRequest` record (`anchorId`, `revisedText`, `source`, `requesterId`) in `anchor/` package
- [x] 1.3 Create `MutationDecision` sealed interface with `Allow`, `Deny(reason)`, `PendingApproval(requestId)` permits in `anchor/` package
- [x] 1.4 Create `AnchorMutationStrategy` sealed interface with `evaluate(MutationRequest)` method in `anchor/` package
- [x] 1.5 Create `HitlOnlyMutationStrategy` implementing `AnchorMutationStrategy`: ALLOW for UI, DENY for LLM_TOOL and CONFLICT_RESOLVER
- [x] 1.6 Write unit tests for `HitlOnlyMutationStrategy`: UI allowed, LLM_TOOL denied, CONFLICT_RESOLVER denied, denial reasons correct

## 2. Configuration and wiring

- [x] 2.1 Add `mutationStrategy` field to `DiceAnchorsProperties.AnchorConfig` with default `hitl-only`
- [x] 2.2 Wire `HitlOnlyMutationStrategy` as `@Service` bean conditional on `anchor.mutation.strategy=hitl-only` (or default)
- [x] 2.3 Write unit tests for configuration defaults and conditional bean selection (covered by HitlOnlyMutationStrategyTest)

## 3. Remove reviseFact LLM tool

- [x] 3.1 Delete `reviseFact()` method from `AnchorTools.java`
- [x] 3.2 Delete `RevisionResult.java` record
- [x] 3.3 Remove revision carveout blocks from `dice-anchors.jinja`: `[revisable]` annotations, `reviseFact` tool instructions in Critical Instructions, revision exception in Verification Protocol
- [x] 3.4 Remove `revision_enabled` and `reliable_revisable` template variable passing from `ChatActions.respond()` and `ChatView.renderChatPrompt()`
- [x] 3.5 Update or delete `DiceAnchorsTemplateTest` revision carveout tests (9 tests in `RevisionCarveout` nested class)
- [x] 3.6 Update any other tests referencing `reviseFact`, `RevisionResult`, or `revision_enabled`

## 4. Gate ChatView revision through SPI

- [x] 4.1 Inject `AnchorMutationStrategy` into `ChatView` via constructor
- [x] 4.2 Update `ChatView.reviseAnchor()` to call `strategy.evaluate()` before executing mutation; pattern-match on `Allow`/`Deny`/`PendingApproval`
- [x] 4.3 Show error notification on `Deny` with the denial reason

## 5. RevisionAwareConflictResolver HITL-only behavior

- [x] 5.1 Inject `AnchorMutationStrategy` into `RevisionAwareConflictResolver`
- [x] 5.2 Update `resolveRevision()`: probe strategy with `CONFLICT_RESOLVER` source; if `Deny`, delegate to `AuthorityConflictResolver`; preserve OTEL span attributes regardless
- [x] 5.3 Write unit tests: REVISION conflict with HITL-only strategy delegates to authority resolver, OTEL attributes still recorded
- [x] 5.4 Write unit tests: REVISION conflict with permissive strategy follows existing authority-gated logic

## 6. Sim panel revision UI

- [x] 6.1 Inject `AnchorMutationStrategy` into `AnchorManipulationPanel` via constructor
- [x] 6.2 Add `REVISE` to `ActionType` enum in `AnchorManipulationPanel`
- [x] 6.3 Add revision text field + "Revise" button to `buildAnchorEditCard()`, gated through `AnchorMutationStrategy`
- [x] 6.4 Log revision as intervention event with `ActionType.REVISE`

## 7. Compile and test verification

- [x] 7.1 Verify `./mvnw clean compile -DskipTests` succeeds
- [x] 7.2 Verify `./mvnw test` passes (all existing + new tests)
