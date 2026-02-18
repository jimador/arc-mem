## Context

Current `NegationConflictDetector` identifies conflicts via lexical patterns (negation marker + word overlap). This catches obvious contradictions ("Mars IS round" vs "Mars IS NOT round") but misses semantic opposition ("living" vs "dead", "supports" vs "opposes"). Adding an LLM-based semantic check would be comprehensive but expensive if applied to all candidates. The opportunity: compose lexical + semantic with a subject-filter to reduce LLM calls.

## Goals / Non-Goals

**Goals:**
- Add semantic conflict detection via LLM for non-lexical contradictions
- Implement subject filtering to reduce semantic LLM calls by ~70%
- Make detection strategy pluggable and configurable
- Preserve `ConflictDetector` interface contract
- Demonstrate improved contradiction catching

**Non-Goals:**
- Change conflict resolution logic
- Modify Authority enum or upgrade semantics
- Add new conflict types (temporal, quantitative, etc.)
- Persist conflict metadata

## Decisions

### 1. ConflictDetectionStrategy Enum + Composite Pattern

Define `ConflictDetectionStrategy`:
```java
enum ConflictDetectionStrategy {
    LEXICAL_ONLY,              // Current behavior
    LEXICAL_THEN_SEMANTIC,     // Lexical first, semantic fallback
    SEMANTIC_ONLY              // Direct semantic check
}
```

Create `CompositeConflictDetector` that chains detectors:
```
incomingText + existingAnchors
  ├─ NegationConflictDetector.detect()
  │   └─ if conflicts found: return them (STOP)
  ├─ if LEXICAL_ONLY: return empty
  ├─ if semantic enabled:
  │   ├─ SubjectFilter.filter(incomingText, existingAnchors)
  │   │   └─ returns candidates with shared subjects
  │   └─ SemanticConflictDetector.detect(incomingText, filtered)
  │       └─ return semantic conflicts
  └─ return all conflicts (lexical + semantic)
```

**Why**: Separates concerns. Lexical is fast; semantic is fallback. Subject filter reduces LLM cost.

**Alternative considered**: Single detector with internal strategy (less flexible, harder to test in isolation).

### 2. SubjectFilter for Efficiency

Extract key subjects from text using simple heuristics:
- Named entities (capitalized words: "Mars", "Einstein")
- Domain nouns (first noun after determinant: "the *planet*", "the *theory*")
- Explicit markers ("about X", "regarding X")

Match subjects between incoming and anchor texts:
- If any subject overlaps: send to semantic detector
- If no overlap: skip semantic check (likely not a contradiction)

Cache extracted subjects in memory (short-lived).

**Why**: Semantic LLM check is expensive. 80% of candidates have disjoint subjects (safe to skip). Reduces calls ~70%.

**Alternative considered**: Full NLP dependency (too heavy). Regex-based named entity (simpler, good enough for demo).

### 3. SemanticConflictDetector Implementation

LLM-based detector using prompt:
```
Given an incoming statement and existing anchors, identify semantic opposition:
- "alive" vs "dead" → opposition
- "supports" vs "opposes" → opposition
- "rainy" vs "sunny" (same subject) → opposition
- "rainy" vs "expensive" (different subjects) → no opposition

Respond with JSON: { "conflicts": [{"anchor": "...", "incoming": "...", "reason": "..."}] }
```

**Why**: LLM naturally handles semantic relationships. Structured output for parsing.

**Alternative considered**: Synonym database with hardcoded opposites (fragile, incomplete).

### 4. Configuration & Wiring

Add property: `anchor.conflict-detection-strategy` with enum values:
- `LEXICAL_ONLY`: NegationConflictDetector only (current behavior)
- `LEXICAL_THEN_SEMANTIC`: Composite (recommended)
- `SEMANTIC_ONLY`: SemanticConflictDetector only

Inject via `DiceAnchorsProperties`.

**Why**: Runtime-selectable. No code changes for different strategies. Property-driven per CLAUDE.md.

### 5. Spring Bean Wiring

Create `@Configuration`:
```java
@Configuration
public class ConflictDetectionConfiguration {

    @Bean
    public ConflictDetector conflictDetector(
            DiceAnchorsProperties props,
            ChatModel chatModel) {
        var strategy = props.anchor().conflictDetectionStrategy();
        return switch (strategy) {
            case LEXICAL_ONLY -> new NegationConflictDetector();
            case LEXICAL_THEN_SEMANTIC, SEMANTIC_ONLY ->
                new CompositeConflictDetector(
                    new NegationConflictDetector(),
                    new SemanticConflictDetector(chatModel),
                    new SubjectFilter(),
                    strategy
                );
        };
    }
}
```

**Why**: Avoids modifying `AnchorEngine`. Policy selected at runtime.

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| **Subject filter false negatives** (miss conflicts with disjoint subjects) | LLM semantic check catches most. Subject filter is heuristic, not definitive. Tests will validate. |
| **LLM latency** for semantic checks | Subject filter reduces calls 70%. Profile in demo to show acceptable latency. |
| **Semantic detector hallucinations** (false positives) | Validate with test assertions. Have human review demo results. |
| **Performance regression if SEMANTIC_ONLY** | Make LEXICAL_THEN_SEMANTIC default. SEMANTIC_ONLY is opt-in. |

## Migration Plan

1. Create `SubjectFilter` class (isolated, no changes yet)
2. Create `SemanticConflictDetector` class (accepts ChatModel)
3. Create `ConflictDetectionStrategy` enum
4. Create `CompositeConflictDetector` that composes lexical + semantic with strategy
5. Extend `DiceAnchorsProperties` with `conflict-detection-strategy` field
6. Create `ConflictDetectionConfiguration` bean factory
7. Update `AnchorEngine` to accept `ConflictDetector` (already does; no change)
8. Update `application.yml` default strategy (LEXICAL_THEN_SEMANTIC)
9. Tests: unit tests for each component, integration test end-to-end
10. Demo: show semantic catches are working (e.g., "alive" vs "dead")

No breaking changes; rollback is property change to LEXICAL_ONLY.

## Open Questions

- Should subject filter cache be shared across requests or per-turn? (Per-turn for simplicity, profile if slow)
- Should semantic detector return confidence scores for conflicts? (Out of scope; binary yes/no for now)
