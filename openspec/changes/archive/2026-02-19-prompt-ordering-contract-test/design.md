## Context

Prompt assembly renders multiple blocks: compliance (anchors), system instructions, task description, conversation history, persona. The hypothesis: compliance block must appear *first* to ground the model before persona/instructions can override it. No existing test validates this ordering. Integration test can assert the invariant.

## Goals / Non-Goals

**Goals:**
- Assert compliance block appears before persona/task blocks in assembled prompt
- Test both DEFAULT (flat) and TIERED (authority-based) compliance policies
- Provide evidence for "framing determines behavior" narrative
- Validate invariant: `indexOf(compliance) < indexOf(persona)`

**Non-Goals:**
- Change prompt assembly logic
- Add performance tests
- Modify prompt templates themselves

## Decisions

### 1. Integration Test Structure

Create `PromptOrderingContractTest` that:
1. Inject `AnchorsLlmReference` + `CompliancePolicy`
2. Create mock anchors with known text
3. Call `contextWithAnchorsJinja()`
4. Assert ordering via `indexOf()` string search

```java
@Tag("integration")
public class PromptOrderingContractTest {
    @Test void complianceBlockPrecedesPersona_DEFAULT_policy() { ... }
    @Test void complianceBlockPrecedesPersona_TIERED_policy() { ... }
    @Test void authorityTiersAppearInOrder_TIERED_policy() { ... }
}
```

**Why**: Simple, direct, validates invariant without mocking internals.

### 2. Test Data

Fixture anchors:
- CANON: "The Earth orbits the Sun"
- RELIABLE: "Earth rotates on its axis"
- UNRELIABLE: "Climate patterns are fixed"
- PROVISIONAL: "Weather prediction is accurate"

These allow testing both compliance and authority-tier ordering.

### 3. Ordering Assertions

Check indices:
```
assert indexOf("CANON") < indexOf("persona")
assert indexOf("RELIABLE") < indexOf("UNRELIABLE")
assert indexOf("UNRELIABLE") < indexOf("PROVISIONAL")
```

**Why**: Direct, readable, validates the core assumption.

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| **Test brittleness to prompt changes** | Test is intentionally strict. If prompt structure changes, update test and document reason. |
| **False confidence** (test passes but LLM ignores ordering) | Live model tests (separate work) will validate behavior. This is structural guarantee. |

## Migration Plan

1. Create test class with both policies tested
2. Run locally to verify passes
3. Add to CI pipeline
4. Document test purpose and invariant it validates

## Open Questions

- Should test include adversarial prompt injection scenarios? (Out of scope; separate adversarial harness)
