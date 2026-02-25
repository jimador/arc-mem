> **Migrated:** This content has been consolidated into [`docs/dev/implementation-notes.md`](dev/implementation-notes.md). This file is retained as a transitional pointer and will be removed in a future cleanup.

---

# Anchor Engine Survey (Tor -> dice-anchors)

## Applied in this pass

- Adopted the RFC 2119 compliance preamble pattern from:
  - `C:/Users/james/Projects/tor/anchor-engine/anchor-engine-core/src/main/java/dev/dunnam/anchor/engine/policy/DefaultPromptAssembler.java`
- Applied to dice-anchors anchor prompt paths:
  - `src/main/resources/prompts/dice-anchors.jinja`
  - `src/main/java/dev/dunnam/diceanchors/assembly/AnchorsLlmReference.java`

## Additional adoption candidates

### 1) Authority-tiered compliance directives
- Source: `C:/Users/james/Projects/tor/anchor-engine/anchor-engine-core/src/main/java/dev/dunnam/anchor/engine/policy/DefaultFactCompliancePolicy.java`
- What to adopt:
  - Map authority levels to different compliance strength (absolute for CANON, softer for PROVISIONAL).
  - Render separate instruction tiers in prompts instead of one flat "MUST NOT contradict" block.

### 2) Token-budgeted prompt assembly
- Sources:
  - `C:/Users/james/Projects/tor/anchor-engine/anchor-engine-core/src/main/java/dev/dunnam/anchor/engine/model/PromptBudget.java`
  - `C:/Users/james/Projects/tor/anchor-engine/anchor-engine-core/src/main/java/dev/dunnam/anchor/engine/spi/TokenCounter.java`
  - `C:/Users/james/Projects/tor/anchor-engine/anchor-engine-core/src/main/java/dev/dunnam/anchor/engine/spi/BudgetedPromptAssembler.java`
- What to adopt:
  - Add optional prompt token budgeting so anchor injection never crowds out system/user context.
  - Keep preamble mandatory; truncate lower-priority details when budget is tight.

### 3) Hybrid conflict detection pipeline
- Sources:
  - `C:/Users/james/Projects/tor/anchor-engine/anchor-engine-core/src/main/java/dev/dunnam/anchor/engine/policy/CompositeConflictDetector.java`
  - `C:/Users/james/Projects/tor/anchor-engine/anchor-engine-core/src/main/java/dev/dunnam/anchor/engine/policy/SemanticConflictDetector.java`
  - `C:/Users/james/Projects/tor/anchor-engine/anchor-engine-core/src/main/java/dev/dunnam/anchor/engine/policy/SubjectFilter.java`
- What to adopt:
  - Run lexical detector first, then semantic detector only on subject-filtered candidates.
  - This should reduce LLM conflict-check calls while improving contradiction coverage.

### 4) Fast+fallback duplicate detection chain
- Sources:
  - `C:/Users/james/Projects/tor/anchor-engine/anchor-engine-core/src/main/java/dev/dunnam/anchor/engine/policy/NormalizedStringDuplicateDetector.java`
  - `C:/Users/james/Projects/tor/anchor-engine/anchor-engine-core/src/main/java/dev/dunnam/anchor/engine/policy/CompositeDuplicateDetector.java`
- What to adopt:
  - Add normalized-string fast pass before current LLM duplicate check in `extract/DuplicateDetector`.
  - Only invoke LLM when fast pass returns novel.

### 5) Lifecycle listener SPI for metrics/audit
- Source: `C:/Users/james/Projects/tor/anchor-engine/anchor-engine-core/src/main/java/dev/dunnam/anchor/engine/spi/AnchorEngineListener.java`
- What to adopt:
  - Add optional listener hooks around create/promote/reinforce/archive/conflict decisions.
  - Use listeners for telemetry and audit trails without mixing concerns into `AnchorEngine`.

### 6) Robust per-context lock implementation
- Source: `C:/Users/james/Projects/tor/anchor-engine/anchor-engine-core/src/main/java/dev/dunnam/anchor/engine/policy/InMemoryAnchorContextLock.java`
- What to adopt:
  - Replace or extend the current simple lock with a per-context lock wrapper (`withLock`) and idle cleanup.
  - This is relevant because chat responses and async extraction both mutate anchor/proposition state.

## Tor OpenSpec review (demo-scope)

This section is based on Tor specs under `C:/Users/james/Projects/tor/openspec/specs/`.
Focus here is "best demo signal per complexity", not production completeness.

### High-value, low-to-medium complexity

### 7) Fast dedup before LLM dedup (demo win: lower cost + less noise)
- Source specs:
  - `C:/Users/james/Projects/tor/openspec/specs/anchor-deduplication/spec.md`
- What to adopt:
  - Add a normalized-string dedup pre-check before `extract/DuplicateDetector` calls the model.
  - Keep current LLM dedup as fallback only.
  - Demo value: cleaner anchor list and fewer duplicate promotions during live demos.

### 8) Composite conflict detection for better contradiction catches
- Source specs:
  - `C:/Users/james/Projects/tor/openspec/specs/semantic-conflict-detection/spec.md`
- What to adopt:
  - Compose existing lexical detector with semantic detection behind a simple pipeline.
  - Add subject pre-filtering to reduce expensive checks.
  - Demo value: stronger "anchors resist drift" examples without deep infrastructure.

### 9) Prompt ordering contract test (compliance block priority)
- Source specs:
  - `C:/Users/james/Projects/tor/openspec/specs/sim-prompt-ordering/spec.md`
- What to adopt:
  - Add tests that assert compliance/anchor block appears before persona instructions in sim prompt assembly.
  - Optionally add targeted adversarial-turn reinforcement text in user message for scripted attacks.
  - Demo value: clearer causality when showing "prompt framing prevents drift".

### 10) Deterministic simulation tests (no live model dependency)
- Source specs:
  - `C:/Users/james/Projects/tor/openspec/specs/sim-test-harness/spec.md`
- What to adopt:
  - Add deterministic simulation tests using canned responses for CI reliability.
  - Keep live-model tests as explicit integration-only runs.
  - Demo value: repeatable proof that anchor mechanics work, even without model variance.

### 11) Lightweight prompt variant A/B runs
- Source specs:
  - `C:/Users/james/Projects/tor/openspec/specs/prompt-variant-testing/spec.md`
- What to adopt:
  - Add minimal prompt variant support (e.g., baseline vs concise/no-verification) for simulation runs.
  - Start with hardcoded variants in test/config before full YAML variant framework.
  - Demo value: direct side-by-side evidence on framing effectiveness.

## Already covered in dice-anchors (no extra adoption needed now)

- Injection toggle and trace/UI visibility:
  - Tor spec: `C:/Users/james/Projects/tor/openspec/specs/anchor-sim-injection-toggle/spec.md`
  - Present in dice-anchors via `ContextTrace.injectionEnabled` and sim panels.
- Core drift metrics:
  - Tor spec: `C:/Users/james/Projects/tor/openspec/specs/drift-evaluation/spec.md`
  - Present in dice-anchors scoring (`factSurvivalRate`, contradiction metrics, attribution count).
- Trust scoring model:
  - Tor spec: `C:/Users/james/Projects/tor/openspec/specs/trust-scoring/spec.md`
  - Present in dice-anchors (`TrustPipeline`, `TrustEvaluator`, signal implementations, profiles).
