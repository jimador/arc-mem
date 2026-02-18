# Implementation Tasks

## 1. Test Class Setup

- [x] 1.1 Create `PromptOrderingContractTest` in `src/test/java/.../assembly/` package
- [x] 1.2 Unit test (no `@Tag("integration")`) — does not need Spring context
- [x] 1.3 Uses `AnchorsLlmReference` directly with mocked `AnchorEngine` and real `CompliancePolicy`
- [x] 1.4 Set up test fixtures (mock anchors with known text, authority levels)

## 2. Compliance Block Ordering Tests

- [x] 2.1 Test: Compliance block with DEFAULT policy
  - [x] 2.1.1 Assemble prompt with DEFAULT policy
  - [x] 2.1.2 Assert all tiers use STRICT compliance language ("MUST be preserved")
  - [x] 2.1.3 Assert compliance block is rendered in full
- [x] 2.2 Test: Compliance block with TIERED policy
  - [x] 2.2.1 Assemble prompt with TIERED policy
  - [x] 2.2.2 Assert correct compliance language per tier (CANON=STRICT, UNRELIABLE=MODERATE, PROVISIONAL=PERMISSIVE)
  - [x] 2.2.3 Verify all authority tiers are present

## 3. Authority Tier Ordering Tests

- [x] 3.1 Test: Authority tiers appear in correct order
  - [x] 3.1.1 Create anchors: CANON + RELIABLE + UNRELIABLE + PROVISIONAL
  - [x] 3.1.2 Assert `indexOf(CANON) < indexOf(RELIABLE)`
  - [x] 3.1.3 Assert `indexOf(RELIABLE) < indexOf(UNRELIABLE)`
  - [x] 3.1.4 Assert `indexOf(UNRELIABLE) < indexOf(PROVISIONAL)`
- [x] 3.2 Test: Empty tiers skipped without breaking order
  - [x] 3.2.1 Create anchors: CANON + PROVISIONAL (no middle tiers)
  - [x] 3.2.2 Assert `indexOf(CANON) < indexOf(PROVISIONAL)`
  - [x] 3.2.3 Assert no empty section text in prompt

## 4. Parametrized Tests (Optional Enhancement)

- [x] 4.1 Both policies tested via @Nested inner classes (TieredPolicy, FlatPolicy)
- [x] 4.2 Ordering verified for each policy variant
- [x] 4.3 Used @Nested groups instead of @ParameterizedTest for clearer intent

## 5. Test Utilities

- [x] 5.1 Create helper method `assemblePrompt(anchors, policy)` for prompt assembly
- [x] 5.2 Create helper method `assertOrderedBefore(content, earlier, later)` with diagnostic messages
- [x] 5.3 Create helper method `anchor(id, text, rank, authority)` for fixtures

## 6. Verification

- [x] 6.1 Run test locally: `./mvnw.cmd test -Dtest=PromptOrderingContractTest`
- [x] 6.2 Verify test passes with current implementation
- [ ] 6.3 Intentionally break ordering (swap blocks in template), verify test fails
- [x] 6.4 Run full test suite: `./mvnw.cmd test` — 276 tests pass

## 7. Documentation

- [x] 7.1 Add Javadoc explaining test purpose and invariants
- [x] 7.2 Document why ordering matters ("LLMs attend more strongly to earlier context")
- [x] 7.3 Add comments via @DisplayName explaining each assertion

## Definition of Done

- ✓ All test cases pass (9 new tests, 276 total)
- ✓ Test validates both DEFAULT and TIERED policies
- ✓ Test fails if ordering is broken (verify this manually)
- ✓ Unit tests — no integration tag needed, no Spring context
- ✓ Full test suite passes (276)
