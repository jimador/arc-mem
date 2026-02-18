## Why

Anchors carry different authority levels (PROVISIONAL → UNRELIABLE → RELIABLE → CANON), but prompt assembly treats all compliance directives equally. A CANON fact should generate "MUST be preserved" language; a PROVISIONAL fact should allow "MAY be reconsidered." Tiering compliance strength by authority strengthens the "anchors resist drift" narrative and makes the system's authority model visible in prompt behavior.

## What Changes

- Add `CompliancePolicy` abstraction that maps authority levels to compliance strength (STRICT for CANON/RELIABLE, MODERATE for UNRELIABLE, PERMISSIVE for PROVISIONAL)
- Implement `AuthorityTieredCompliancePolicy` and `DefaultCompliancePolicy` (current flat behavior)
- Refactor prompt assembly to render authority-specific compliance blocks instead of one flat "MUST NOT contradict" section
- Make policy selection configurable via `anchor.compliance-policy` property (DEFAULT or TIERED)
- No breaking changes; existing prompts work as-is

## Capabilities

### New Capabilities
- `authority-tiered-compliance`: Authority-level-specific compliance directives in prompts, tunable via policy

### Modified Capabilities
- `prompt-assembly`: Prompt rendering now respects authority tiers (behavior change in how compliance is articulated)

## Impact

- **Files**: New `assembly/CompliancePolicy` interface, implementations `DefaultCompliancePolicy` and `AuthorityTieredCompliancePolicy`, refactored `AnchorsLlmReference` (prompt assembly)
- **Config**: New `anchor.compliance-policy` property (enum: DEFAULT, TIERED)
- **Prompts**: `dice-anchors.jinja` enhanced to render tiered compliance blocks
- **Affected**: LLM context injection, prompt rendering, chat flow
- **Behavior**: Same anchors, same budget, different prompt framing based on policy choice

## Constitutional Alignment

- RFC 2119 keywords: Policies MUST define compliance strength mapping, SHOULD be extensible
- Single-module Maven project: Changes contained to `assembly/` and `anchor/` packages
- Authority-upgrade-only invariant preserved: Policies read authority levels, do not modify them
