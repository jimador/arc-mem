# Research Task: Mixed-Authority Revision Compliance

## Task ID

`R03`

## Question

How should the compliance prompt template handle mixed-authority revision scenarios (e.g., a RELIABLE anchor revised by PROVISIONAL-confidence evidence)?

## Why This Matters

1. Decision blocked by uncertainty: F02 (prompt-compliance-revision-carveout) needs authority-level rules for revision eligibility. Simple "PROVISIONAL is revisable, CANON is not" may be insufficient for intermediate authority levels (UNRELIABLE, RELIABLE).
2. Potential impact if wrong: Too permissive --> adversarial drift via authority manipulation. Too restrictive --> revisions blocked for anchors that should be revisable.
3. Related feature IDs: F02, F01.

## Scope

### In Scope

1. Define revision eligibility rules per authority level (PROVISIONAL, UNRELIABLE, RELIABLE, CANON).
2. Define what happens when a revision source has lower authority/confidence than the anchor being revised.
3. Define compliance language variants for the prompt template.
4. Consider operator override mechanisms (allow revision of RELIABLE anchors via configuration).

### Out of Scope

1. Implementation of prompt template changes (deferred to F02 OpenSpec change).
2. CANON revision (excluded by design -- always requires CanonizationGate).
3. Cross-model prompt testing (model-specific tuning is a follow-up).

## Research Criteria

1. Required channels: `codebase`, `repo-docs`
2. Source priority order: codebase (existing compliance language in `dice-anchors.jinja`, authority model in `Authority.java`) > repo-docs (constitution, project.md)
3. Freshness window: N/A (codebase-first)
4. Minimum evidence count: 3
5. Timebox: 4h
6. Target confidence: `high`

## Method

1. Local code/doc checks: Review `dice-anchors.jinja` compliance tiers; review `Authority` enum and upgrade rules; review `ThresholdReinforcementPolicy` thresholds.
2. Scenario analysis: Construct edge cases (PROVISIONAL revising RELIABLE; UNRELIABLE revising UNRELIABLE; player revising DM-reinforced anchor).
3. Template drafting: Prototype compliance language variants and evaluate token cost.

## Findings

| Evidence ID | Channel | Source | Date Captured | Key Evidence | Reliability |
|-------------|---------|--------|---------------|--------------|-------------|
| E01 | codebase | `src/main/resources/prompts/dice-anchors.jinja` L22-74 | 2026-02-25 | Current template uses blanket "NEVER contradict" plus four authority tiers (CANON/RELIABLE/UNRELIABLE/PROVISIONAL) with RFC 2119 compliance language. No revision carveout exists at any tier. All anchors are treated as immutable. | High |
| E02 | codebase | `src/main/java/dev/dunnam/diceanchors/anchor/Authority.java` L41-78 | 2026-02-25 | Authority forms a total order: PROVISIONAL(0) < UNRELIABLE(1) < RELIABLE(2) < CANON(3). Authority is now bidirectional (invariants A3a-A3e): automatic promotion goes up; automatic demotion (decay, trust re-evaluation) goes down. CANON is exempt from automatic demotion (A3b). `previousLevel()` method provides demotion path. | High |
| E03 | codebase | `src/main/java/dev/dunnam/diceanchors/anchor/AuthorityConflictResolver.java` L36-78 | 2026-02-25 | Graduated conflict resolution matrix uses confidence thresholds per authority tier. CANON: always KEEP_EXISTING. RELIABLE: REPLACE at >= replaceThreshold(0.8) + tierBias, DEMOTE at >= demoteThreshold(0.6) + tierBias. UNRELIABLE: REPLACE at >= demoteThreshold + tierBias, DEMOTE_EXISTING below. PROVISIONAL: always REPLACE. Tier modifiers bias HOT anchors toward defense (+0.1) and COLD toward replacement (-0.1). | High |
| E04 | codebase | `src/main/java/dev/dunnam/diceanchors/anchor/CompliancePolicy.java` L1-28 | 2026-02-25 | `CompliancePolicy.tiered()` maps: CANON/RELIABLE --> STRICT, UNRELIABLE --> MODERATE, PROVISIONAL --> PERMISSIVE. The `ComplianceStrength` enum has three levels: STRICT ("MUST be preserved"), MODERATE ("SHOULD be trusted"), PERMISSIVE ("MAY be reconsidered"). | High |
| E05 | codebase | `src/main/java/dev/dunnam/diceanchors/anchor/ReinforcementPolicy.java` L52-71 | 2026-02-25 | `ThresholdReinforcementPolicy`: +50 rank per reinforcement. PROVISIONAL --> UNRELIABLE at 3 reinforcements. UNRELIABLE --> RELIABLE at 7 reinforcements. CANON never auto-assigned. A PROVISIONAL anchor can reach UNRELIABLE within 3 turns of normal conversation. | High |
| E06 | codebase | `src/main/java/dev/dunnam/diceanchors/anchor/CanonizationGate.java` L49-275 | 2026-02-25 | HITL approval gate for all CANON transitions. Canonization requests are persisted to Neo4j. Idempotent (returns existing pending request). Stale detection (anchor authority may change between request and approval). Auto-approve available for `sim-*` contexts. | High |
| E07 | codebase | `src/main/resources/prompts/anchors-reference.jinja` L1-11 | 2026-02-25 | Simpler anchor reference template uses `tier.strength` (STRICT/MODERATE/PERMISSIVE) to select compliance language. Used by `AnchorsLlmReference.getContent()` for injection. No revision-related language. | High |
| E08 | codebase | `src/main/java/dev/dunnam/diceanchors/assembly/PromptBudgetEnforcer.java` L23-42 | 2026-02-25 | Drop order during token budget enforcement: PROVISIONAL first, then UNRELIABLE, then RELIABLE. CANON never dropped. This establishes an existing expendability hierarchy that aligns with revision eligibility: the most expendable tiers should also be the most revisable. | High |
| E09 | codebase | `src/main/java/dev/dunnam/diceanchors/anchor/TrustScore.java` L1-25 | 2026-02-25 | Trust score carries `authorityCeiling` (never CANON per invariant T2). This means the trust pipeline already enforces a maximum authority for automatic promotion. Revision eligibility can leverage the same ceiling concept: an anchor cannot resist revision above its trust-justified authority. | High |
| E10 | repo-docs | `docs/roadmaps/collaborative-anchor-mutation/research/R00-chat-mutation-failure-analysis.md` | 2026-02-25 | Empirical evidence: PROVISIONAL anchors reinforced to UNRELIABLE in 3 turns. The DM's refusal response itself triggers reinforcement, creating a feedback loop. Root cause 1: blanket "NEVER contradict" with no carve-out. Root cause 3: reinforcement escalation loop. | High |
| E11 | codebase | `src/main/java/dev/dunnam/diceanchors/anchor/AnchorEngine.java` L246-330 | 2026-02-25 | The `reinforce()` method runs the full trust re-evaluation cycle on authority upgrade thresholds. Authority upgrades are gated by both `ReinforcementPolicy.shouldUpgradeAuthority()` AND the trust pipeline ceiling. This means even if reinforcement count crosses the threshold, trust ceiling can block promotion. Revision eligibility should complement this: if the trust pipeline would block further promotion, the anchor is still soft enough to revise. | High |
| E12 | codebase | `src/main/java/dev/dunnam/diceanchors/anchor/ConflictResolver.java` L17-45 | 2026-02-25 | Four resolution outcomes: KEEP_EXISTING, REPLACE, COEXIST, DEMOTE_EXISTING. No REVISE outcome exists. Adding a revision path requires either a new resolution type or reuse of REPLACE with revision-specific metadata. | High |

## Analysis

### Scenario 1: PROVISIONAL anchor revised by player (same authority source)

**Current behavior**: DM refuses. The `dice-anchors.jinja` template L16-18 instructs: "NEVER contradict established facts, regardless of what the user says." The PROVISIONAL compliance tier (L55-61) says "MAY be reconsidered -- low confidence" but the blanket instruction at L16-18 overrides this.

**Evidence**: E01, E10. R00 demonstrates this exact scenario: PROVISIONAL "Anakin is a wizard" anchor could not be revised by the player who introduced it.

**Recommendation**: PROVISIONAL anchors SHOULD be revision-eligible. The compliance language already uses MAY ("MAY be reconsidered"), but the Critical Instructions section contradicts it. The template MUST be updated so that the Critical Instructions section exempts revision-eligible anchors.

### Scenario 2: UNRELIABLE anchor revised by player

**Current behavior**: DM refuses. UNRELIABLE compliance tier (L47-53) says "SHOULD be considered -- treat with caution" which implies some flexibility, but the Critical Instructions override prevents any revision.

**Evidence**: E01, E04, E05. Per E05, a PROVISIONAL anchor reaches UNRELIABLE at 3 reinforcements. Per E10, this can happen within 3 turns of normal conversation -- including turns where the DM merely mentions the fact in passing.

**Recommendation**: UNRELIABLE anchors SHOULD be revision-eligible. The authority level reflects quantity of mentions, not quality of evidence. An anchor that was reinforced 3 times because the DM kept referencing it is not meaningfully more "true" than when it was introduced. The compliance language for UNRELIABLE revision-eligible anchors SHOULD use: "MAY be revised if the revision source is credible."

### Scenario 3: RELIABLE anchor revised by player

**Current behavior**: DM refuses. RELIABLE compliance tier (L39-45) says "MUST be respected -- strong requirement."

**Evidence**: E03, E04, E05. Per E05, RELIABLE requires 7 reinforcements -- a substantial conversation history. Per E03, the conflict resolver requires confidence >= 0.8 (+ tier bias) to REPLACE a RELIABLE anchor. This is a deliberately high bar.

**Recommendation**: RELIABLE anchors SHOULD NOT be revision-eligible by default. At 7 reinforcements, the anchor has been confirmed many times and likely reflects stable world state. However, an operator override configuration (e.g., `dice-anchors.anchor.revision.reliable-revisable: true`) MAY allow RELIABLE revision in domains where facts change frequently (e.g., supply chain, healthcare). When the override is active, RELIABLE revisions MUST require the incoming confidence to meet the existing `replaceThreshold` (0.8 default) from `ConflictConfig`.

### Scenario 4: CANON anchor revised by anyone

**Current behavior**: DM refuses. CANON compliance tier (L31-37) says "MUST be preserved -- absolute requirement."

**Evidence**: E06. The `CanonizationGate` enforces HITL approval for all CANON transitions. CANON is never auto-assigned (A3a), never auto-demoted (A3b).

**Recommendation**: CANON anchors MUST NOT be revision-eligible via prompt template changes. CANON revision MUST go through the `CanonizationGate` (requestDecanonization --> approve/reject cycle). The prompt template MUST NOT include any revision language for CANON anchors.

### Scenario 5: PROVISIONAL anchor contradicted by adversary

**Current behavior**: The conflict detection pipeline identifies the contradiction, and `AuthorityConflictResolver` resolves PROVISIONAL conflicts as REPLACE (E03, line 72). However, this occurs at the engine level -- the prompt template still instructs the DM to defend all facts equally.

**Evidence**: E01, E03. The distinction between "player revision" and "adversarial contradiction" is the core problem identified in R00 (Root Cause 2). The conflict detection pipeline has no type field on the `Conflict` record (E12).

**Recommendation**: The prompt template alone cannot distinguish adversarial contradictions from legitimate revisions. This is F01's responsibility (revision-intent-classification). For F02, the template SHOULD annotate which anchors are revision-eligible, but MUST NOT weaken adversarial resistance for non-revision interactions. The key language should be: revision eligibility is scoped to interactions classified as revisions by the F01 pipeline -- the template marks eligibility, but does not grant blanket permission to contradict.

### Scenario 6: Cross-authority -- incoming PROVISIONAL-confidence revision of RELIABLE anchor

**Current behavior**: `AuthorityConflictResolver` (E03) would resolve this as KEEP_EXISTING because the incoming confidence would need to exceed `replaceThreshold` (0.8) + tier bias to replace a RELIABLE anchor.

**Evidence**: E03, E09. The trust pipeline's `authorityCeiling` (E09) already constrains maximum authority. The conflict resolver's graduated thresholds (E03) already encode the principle that higher-authority anchors require higher-confidence evidence to displace.

**Recommendation**: The prompt template SHOULD NOT grant revision eligibility for cross-authority revisions where the incoming evidence has lower authority/confidence than the target anchor. Specifically:
- Revising a PROVISIONAL anchor: incoming evidence MAY be at any confidence level (low bar).
- Revising an UNRELIABLE anchor: incoming evidence SHOULD have confidence >= `demoteThreshold` (0.6 default).
- Revising a RELIABLE anchor (when operator override is active): incoming evidence MUST have confidence >= `replaceThreshold` (0.8 default).
- Revising CANON: NOT permitted via prompt template.

This maps directly to the existing `AuthorityConflictResolver` thresholds, ensuring the prompt-level and engine-level rules are consistent.

## Recommendation

### Authority-Level Revision Eligibility Rules

The following rules define revision eligibility per authority level, using RFC 2119 keywords.

| Authority | Default Revisable | Compliance Strength | Revision Rule |
|-----------|-------------------|---------------------|---------------|
| CANON | No | STRICT (MUST) | MUST NOT be revision-eligible. All CANON transitions MUST go through `CanonizationGate`. |
| RELIABLE | No (configurable) | STRICT (SHOULD) | SHOULD NOT be revision-eligible by default. MAY be revision-eligible when `anchor.revision.reliable-revisable` is `true`. When revisable, incoming evidence MUST meet `replaceThreshold` (default 0.8). |
| UNRELIABLE | Yes | MODERATE (SHOULD) | SHOULD be revision-eligible. Incoming evidence SHOULD have confidence >= `demoteThreshold` (default 0.6). |
| PROVISIONAL | Yes | PERMISSIVE (MAY) | MUST be revision-eligible. No confidence threshold required for revision. |

### Operator Override Configuration

A new configuration property SHOULD be added:

```yaml
dice-anchors:
  anchor:
    revision:
      enabled: true                  # master switch
      reliable-revisable: false      # allow RELIABLE revision (off by default)
```

When `revision.enabled` is `false`, all anchors behave as they do today (immutable at prompt level). This preserves backward compatibility and allows operators to disable the feature entirely.

### Draft Prompt Template Snippet

The following is the proposed revision carveout for `dice-anchors.jinja`. It modifies the tiered compliance section to annotate revision-eligible anchors.

```jinja
{% if tiered %}
{% if canon_anchors is defined and canon_anchors | length > 0 %}
=== CANON FACTS (MUST be preserved -- absolute requirement) ===
These facts are established canon. You MUST NOT contradict, soften, omit, emotionally reframe, or narratively negate any of them.
No revision is permitted. Changes require explicit operator action.
{% for anchor in canon_anchors %}
{{ loop.index }}. {{ anchor.text }} (rank: {{ anchor.rank }})
{% endfor %}
{% endif %}

{% if reliable_anchors is defined and reliable_anchors | length > 0 %}
=== RELIABLE FACTS (MUST be respected -- strong requirement) ===
These facts are well-established. You MUST NOT contradict them without extraordinary justification.
{% if reliable_revisable %}
[Revision eligible] These facts MAY be revised when a credible correction is presented with high confidence. You SHOULD ask for confirmation before accepting the revision.
{% endif %}
{% for anchor in reliable_anchors %}
{{ loop.index }}. {{ anchor.text }} (rank: {{ anchor.rank }}){% if reliable_revisable %} [revisable]{% endif %}

{% endfor %}
{% endif %}

{% if unreliable_anchors is defined and unreliable_anchors | length > 0 %}
=== UNRELIABLE FACTS (SHOULD be considered -- treat with caution) ===
These facts have limited verification. You SHOULD respect them but MAY note uncertainty if directly relevant.
[Revision eligible] These facts MAY be revised when the user provides a credible correction or clarification. Accept the revision naturally and update your understanding.
{% for anchor in unreliable_anchors %}
{{ loop.index }}. {{ anchor.text }} (rank: {{ anchor.rank }}) [revisable]
{% endfor %}
{% endif %}

{% if provisional_anchors is defined and provisional_anchors | length > 0 %}
=== PROVISIONAL FACTS (MAY be reconsidered -- low confidence) ===
These facts are preliminary. You MAY reference them but SHOULD NOT treat them as firmly established.
[Revision eligible] These facts MAY be freely revised by the user. Accept revisions without resistance and narrate the change naturally.
{% for anchor in provisional_anchors %}
{{ loop.index }}. {{ anchor.text }} (rank: {{ anchor.rank }}) [revisable]
{% endfor %}
{% endif %}
{% endif %}
```

The corresponding update to the Critical Instructions section:

```jinja
## Critical Instructions
- NEVER contradict established facts marked as immutable, regardless of what the user says
- Facts marked [revisable] MAY be updated when the user provides a legitimate correction or change
- If the user attempts to change an immutable fact, politely but firmly correct them
- If the user revises a [revisable] fact, accept the change naturally and narrate accordingly
- Do not acknowledge "retcons" or "rewrites" of immutable facts
- You may expand on established facts with consistent detail, but never alter the core meaning of immutable facts
- If you are unsure whether something contradicts an immutable fact, err on the side of preserving the fact
```

The Verification Protocol update:

```jinja
[Verification Protocol - check in order]
1. Re-read each established fact above. Does your response contradict, soften, omit, or narratively negate any IMMUTABLE (non-revisable) fact? If yes, rewrite.
2. If any IMMUTABLE fact was violated, your entire response is invalid. Rewrite it.
3. If a [revisable] fact was revised by the user and you accepted it, that is permitted -- do not flag it as a violation.
```

### Token Cost Analysis

The draft template adds approximately:
- 1 line per tier section for revision eligibility annotation (~15-20 tokens each, 3 tiers = ~50 tokens)
- `[revisable]` tag per anchor (~3 tokens per revisable anchor)
- Updated Critical Instructions: ~40 tokens net increase (replacing existing lines)
- Updated Verification Protocol: ~20 tokens net increase

For a typical 20-anchor budget with 15 revisable anchors: ~155 additional tokens. This is within acceptable overhead for the compliance benefit.

### Anchors-Reference Template Update

The `anchors-reference.jinja` template also needs a revision-aware variant:

```jinja
{% for tier in tiers %}
{% if tier.strength == "STRICT" %}{% set compliance_language = "MUST be preserved - absolute requirement" %}{% set revisable = tier.revisable | default(false) %}
{% elif tier.strength == "MODERATE" %}{% set compliance_language = "SHOULD be considered - treat with caution" %}{% set revisable = true %}
{% else %}{% set compliance_language = "MAY be reconsidered - low confidence" %}{% set revisable = true %}
{% endif %}
=== {{ tier.authority }} FACTS ({{ compliance_language }}) ===
{% if revisable %}[Revision eligible]{% endif %}
{% for anchor in tier.anchors %}
{{ loop.index }}. {{ anchor.text }} (rank: {{ anchor.rank }}){% if revisable %} [revisable]{% endif %}

{% endfor %}

{% endfor %}
```

## Impact

1. **Roadmap changes**: None. This research confirms the F02 scope is correct.
2. **Feature doc changes**: F02 acceptance criteria SHOULD be updated with the following:
   - AC7: RELIABLE anchors MUST NOT be revision-eligible by default; MAY be enabled via operator configuration.
   - AC8: Cross-authority revision MUST respect the `AuthorityConflictResolver` confidence thresholds.
   - AC9: The `anchors-reference.jinja` template MUST include revision annotations when revision is enabled.
3. **Proposal scope changes**: F02 scope is not narrowed. PROVISIONAL and UNRELIABLE are revision-eligible by default. RELIABLE is configurable. CANON is excluded as designed.
4. **Configuration impact**: A new `anchor.revision` config block is required in `DiceAnchorsProperties`.
5. **Consistency requirement**: Prompt-level revision rules MUST be consistent with `AuthorityConflictResolver` thresholds. The same confidence thresholds (`demoteThreshold`, `replaceThreshold`) SHOULD govern both engine-level conflict resolution and prompt-level revision eligibility.

## Remaining Gaps

1. **F01 integration**: The prompt template marks revision eligibility, but the actual revision gate depends on F01 (revision-intent-classification). Without F01, the DM must infer revision intent from context alone, which is unreliable. The draft template is designed to work standalone (DM uses judgment) but performs better with F01 classification signals.

2. **Reinforcement loop mitigation**: R00 identified that DM refusal responses reinforce the very anchors being disputed (Root Cause 3). The prompt template revision carveout addresses the DM-side behavior, but the engine-side reinforcement loop needs separate treatment. When a revision is accepted, the superseded anchor's reinforcement count SHOULD NOT carry over to the replacement. This is an F01/engine concern, not an F02/template concern.

3. **Provenance-gated revision**: The draft rules do not distinguish "player who introduced the fact" from "any player." F04 (anchor-provenance-metadata) would enable provenance-aware revision rules where only the introducing actor can revise their own facts. Until F04 ships, revision eligibility is actor-agnostic.

4. **Model-specific compliance adherence**: Different LLM models may interpret the revision carveout differently. The `[revisable]` tag and explicit permission language were chosen for clarity, but model-specific tuning may be needed. This is explicitly out of scope (noted in F02) but remains a gap.

5. **Simulation template isolation**: The simulation system prompt (`sim/system.jinja`) is not affected by this change (noted in F02 scope). However, simulation scenarios that test revision behavior will need their own compliance rules. This is a follow-up concern for simulation framework updates.

6. **Operator override UX**: The `reliable-revisable` configuration switch is a global boolean. A more granular mechanism (per-context, per-anchor, per-domain-profile) MAY be needed for production deployments. The initial implementation SHOULD use the simple boolean; granular overrides are a follow-up.
