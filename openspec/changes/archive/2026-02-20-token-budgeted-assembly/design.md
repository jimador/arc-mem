# Design: Token-Budgeted Assembly

## 1. Architecture

```
AnchorsLlmReference.getContent()
    │
    ├── engine.inject(contextId) → List<Anchor>
    │
    ├── IF tokenBudget > 0:
    │   └── PromptBudgetEnforcer.enforce(anchors, tokenBudget, tokenCounter)
    │       → BudgetResult(included, excluded, tokensUsed, exceeded)
    │
    ├── Render authority-tiered blocks (using included anchors only)
    │
    └── Return assembled string
```

## 2. New Types

### TokenCounter (SPI)
```java
// assembly/TokenCounter.java
public interface TokenCounter {
    int estimate(String text);
}
```

### CharHeuristicTokenCounter (default)
```java
// assembly/CharHeuristicTokenCounter.java
public class CharHeuristicTokenCounter implements TokenCounter {
    private static final int CHARS_PER_TOKEN = 4;

    @Override
    public int estimate(String text) {
        return text == null ? 0 : text.length() / CHARS_PER_TOKEN;
    }
}
```

### PromptBudgetEnforcer
```java
// assembly/PromptBudgetEnforcer.java
public class PromptBudgetEnforcer {
    private static final List<Authority> TRUNCATION_ORDER =
        List.of(Authority.PROVISIONAL, Authority.UNRELIABLE, Authority.RELIABLE);
    // CANON never in truncation order

    public BudgetResult enforce(List<Anchor> anchors, int tokenBudget,
                                 TokenCounter counter, CompliancePolicy policy) {
        // 1. Estimate mandatory overhead (preamble + verification protocol)
        // 2. Group anchors by authority
        // 3. Always include CANON anchors
        // 4. Add remaining tiers until budget exhausted, following TRUNCATION_ORDER in reverse
        //    (RELIABLE first, then UNRELIABLE, then PROVISIONAL — since we're ADDING, not dropping)
        //    Actually: start with all, drop PROVISIONAL first until fits
        // 5. Return BudgetResult
    }
}
```

### BudgetResult
```java
// assembly/BudgetResult.java
public record BudgetResult(
    List<Anchor> included,
    List<Anchor> excluded,
    int estimatedTokens,
    boolean budgetExceeded
) {}
```

## 3. AnchorsLlmReference Modification

Add `tokenBudget` and `tokenCounter` to constructor:

```java
public AnchorsLlmReference(AnchorEngine engine, String contextId, int budget,
                           CompliancePolicy compliancePolicy,
                           int tokenBudget,           // NEW (0 = disabled)
                           TokenCounter tokenCounter)  // NEW (nullable when disabled)
```

In `getContent()`, after retrieving anchors and before rendering:
```java
var limited = cachedAnchors.stream().limit(budget).toList();

if (tokenBudget > 0 && tokenCounter != null) {
    var budgetResult = new PromptBudgetEnforcer()
        .enforce(limited, tokenBudget, tokenCounter, compliancePolicy);
    limited = budgetResult.included();
    // Store budgetResult for telemetry access
    this.lastBudgetResult = budgetResult;
}

var grouped = limited.stream()
    .collect(Collectors.groupingBy(Anchor::authority));
// ... render as before
```

## 4. Configuration

Add to `DiceAnchorsProperties`:
```java
@NestedConfigurationProperty AssemblyConfig assembly
```

```java
public record AssemblyConfig(
    @DefaultValue("0") int promptTokenBudget  // 0 = disabled
) {}
```

In `application.yml`:
```yaml
dice-anchors:
  assembly:
    prompt-token-budget: 0  # disabled by default
```

## 5. ContextTrace Extension

Add two fields to ContextTrace:
```java
boolean budgetApplied,
int anchorsExcluded
```

With convenience constructor defaulting both to `false`/`0` for backward compatibility.

## 6. SimulationView UI Control

Add a numeric input to the scenario settings panel in `SimulationView`:
- Label: "Token Budget (0=off)"
- Range: 0-5000
- Default: 0
- Stored as override on the scenario config, passed through to `SimulationTurnExecutor` → `AnchorsLlmReference`

Implementation: Add `IntegerField` in the settings layout, bind to a `tokenBudget` field on the view, pass through `SimulationService.runSimulation()` → `SimulationTurnExecutor.executeTurn()`.

## 7. SimulationTurnExecutor Integration

`SimulationTurnExecutor` already creates `AnchorsLlmReference`. Pass the budget config:
```java
var ref = new AnchorsLlmReference(engine, contextId, budget, compliancePolicy,
                                   tokenBudget, tokenCounter);
```

Where `tokenBudget` comes from either properties or UI override.

## 8. Key Design Decisions

1. **Budget in AnchorsLlmReference, not a separate assembler** — Keeps the change minimal. AnchorsLlmReference is already where rendering happens; adding budget enforcement here avoids a new abstraction layer.
2. **Drop entire anchors, not truncate text** — Partial anchor text would break semantic coherence. Dropping the lowest-authority anchor entirely is cleaner.
3. **CANON never dropped** — Even if budget is exceeded, CANON facts are too important to remove. Budget is best-effort.
4. **0 = disabled** — Simple, backward-compatible default. No special "enabled" boolean needed.
5. **CharHeuristicTokenCounter as default** — Matches existing `length/4` pattern in codebase. No new dependencies.
6. **UI override via SimulationView** — Budget can be set per-scenario from the UI, matching the Tor project's sim app pattern.

## 9. Non-Goals

- No real tokenizer integration (char heuristic is sufficient for demo)
- No per-model token counting
- No chat UI budget controls (sim only for now)
- No history truncation (only anchor block is budgeted)
