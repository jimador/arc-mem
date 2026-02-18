## Why

Prompt framing determines LLM behavior. Compliance blocks should appear before persona/task instructions to establish grounding *first*. A contract test asserting this ordering directly validates "prompts determine behavior" and provides confidence that authority-tiered compliance directives have priority when rendered.

## What Changes

- Add integration test asserting compliance/anchor block appears before persona/task instructions in assembled prompt
- Test runs against both DEFAULT and TIERED authority policies
- Optionally add adversarial turn detection that triggers reinforcement text in sim scenarios
- No production code changes; test-only

## Capabilities

### New Capabilities
- `prompt-ordering-contract`: Integration tests validating compliance block precedence in prompt assembly

### Modified Capabilities
(None - test-only change)

## Impact

- **Files**: New `src/test/java/.../PromptOrderingContractTest.java`
- **APIs**: No production changes
- **Config**: No changes
- **Affected**: Test suite only (demonstrates existing behavior, validates invariant)
- **Value**: Confidence in prompt structure; evidence for "framing matters" narrative

## Constitutional Alignment

- RFC 2119 keywords: Ordering contract MUST be validated per policy; tests SHOULD verify both DEFAULT and TIERED
- Integration test isolation: clean context per test
