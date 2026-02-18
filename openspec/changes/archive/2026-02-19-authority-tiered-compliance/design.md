## Context

Current prompt assembly in `AnchorsLlmReference` renders all anchors with a flat compliance directive: "MUST NOT contradict the following facts" regardless of authority level. The codebase already has the `Authority` enum (PROVISIONAL, UNRELIABLE, RELIABLE, CANON) and `Anchor` objects carry authority, but prompts don't leverage this information. Opportunity: render compliance strength based on authority tier.

## Goals / Non-Goals

**Goals:**
- Render authority-specific compliance blocks (MUST for CANON, SHOULD for RELIABLE, MAY for PROVISIONAL)
- Make compliance policy pluggable and configurable
- Preserve existing behavior via DEFAULT policy (flat compliance for all)
- Demonstrate that authority model is visible in prompt behavior

**Non-Goals:**
- Change Authority enum or upgrade semantics
- Persist compliance strength or policy choice
- Add new authority levels
- Modify budget or rank mechanisms

## Decisions

### 1. CompliancePolicy Interface Pattern

Create `CompliancePolicy` interface with pluggable implementations:
```java
public interface CompliancePolicy {
    ComplianceStrength getStrengthFor(Authority authority);
}

enum ComplianceStrength {
    STRICT,    // "MUST be preserved" - for CANON/RELIABLE
    MODERATE,  // "SHOULD be trusted" - for UNRELIABLE
    PERMISSIVE // "MAY be reconsidered" - for PROVISIONAL
}
```

Implementations:
- `DefaultCompliancePolicy`: all authorities → STRICT (current behavior, flat)
- `AuthorityTieredCompliancePolicy`: maps each authority to its tier

**Why**: Keeps policy isolated and testable. Pluggable via Spring @Bean. No changes to existing Authority enum.

**Alternative considered**: Hardcode mapping in AnchorsLlmReference (less flexible, harder to test).

### 2. Prompt Template Rendering

In `dice-anchors.jinja`, instead of one "MUST NOT contradict" block, render tiered blocks:
```jinja
{% if canon_anchors %}
## CANON Facts (MUST be preserved)
{{ canon_anchors | join("\n") }}
{% endif %}

{% if reliable_anchors %}
## RELIABLE Facts (SHOULD be trusted)
{{ reliable_anchors | join("\n") }}
{% endif %}

{% if unreliable_anchors %}
## UNRELIABLE Facts (MAY be questioned)
{{ unreliable_anchors | join("\n") }}
{% endif %}

{% if provisional_anchors %}
## PROVISIONAL Facts (MAY be reconsidered)
{{ provisional_anchors | join("\n") }}
{% endif %}
```

**Why**: Clear visual hierarchy in prompts. Shows authority model directly. Easy to customize language per tier.

**Alternative considered**: Single block with inline authority labels (less clear, cluttered).

### 3. AnchorsLlmReference Refactor

`AnchorsLlmReference` will:
1. Inject `CompliancePolicy`
2. In `contextWithAnchorsJinja()`, group anchors by authority
3. Map each group through policy to get compliance strength
4. Pass structured data to Jinja template: `List<AuthorityTierBundle>`

```
AuthorityTierBundle {
  authority: Authority
  strength: ComplianceStrength
  anchors: List<Anchor>
}
```

**Why**: Separates policy logic (getStrengthFor) from template rendering. Template is thin and readable.

### 4. Configuration

Add property: `anchor.compliance-policy` with values:
- `DEFAULT`: Flat compliance (all anchors treated equally)
- `TIERED`: Authority-based compliance strength

Inject via `DiceAnchorsProperties`:
```java
@ConfigurationProperties(prefix = "anchor")
public record DiceAnchorsConfig(
    ComplianceMode compliancePolicy,
    // ... existing fields
) {}

enum ComplianceMode {
    DEFAULT, TIERED
}
```

**Why**: Runtime-selectable, doesn't require code changes. Property-driven per CLAUDE.md.

### 5. Spring Bean Wiring

Create `@Configuration` class:
```java
@Configuration
public class ComplianceConfiguration {

    @Bean
    public CompliancePolicy compliancePolicy(
            DiceAnchorsProperties props) {
        return props.anchor().compliancePolicy() == TIERED
            ? new AuthorityTieredCompliancePolicy()
            : new DefaultCompliancePolicy();
    }
}
```

**Why**: No code changes to `AnchorsLlmReference`; policy is injected.

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| **Prompt bloat**: Tiered layout may make prompts longer | Acceptable; compliance blocks are typically small. Tests will validate prompt size. |
| **LLM confusion**: Model sees authority tiers for first time | Test with live model to ensure behavior doesn't regress. Run deterministic tests first. |
| **Policy extensibility pressure**: Teams want custom policies | Keep interface simple; composition over inheritance. Document extension pattern. |
| **Backward compatibility**: Existing demo scenarios expect flat format | DEFAULT policy provides current behavior; explicitly test both. |

## Migration Plan

1. Create `CompliancePolicy` interface and implementations (isolated, no refactoring yet)
2. Extend `DiceAnchorsProperties` with `compliance-policy` field
3. Create `ComplianceConfiguration` Spring bean factory
4. Update `AnchorsLlmReference` to use policy
5. Update `dice-anchors.jinja` template to render tiered blocks
6. Update `application.yml` default to TIERED
7. Tests: verify both DEFAULT and TIERED produce correct prompts
8. Demo: show side-by-side comparison (flat vs tiered)

No breaking changes; rollback is property change to DEFAULT.

## Open Questions

- Should compliance strength affect token budgeting (STRICT takes more budget)? (Out of scope; separate investigation)
- Should users see policy selection in UI? (Out of scope; not in chat/sim views currently)
