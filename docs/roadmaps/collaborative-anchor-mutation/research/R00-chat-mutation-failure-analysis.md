# R00: Chat UI Mutation Failure Analysis

## Purpose

Empirical evidence from a Playwright-driven end-to-end test of the Chat UI demonstrating that the anchor framework cannot distinguish legitimate revisions from adversarial contradictions. This document serves as the motivating research input for the Collaborative Anchor Mutation roadmap.

## Test Environment

- **App**: dice-anchors running locally at `http://localhost:8089/chat`
- **LLM**: gpt-4.1-mini (via Embabel Agent)
- **Browser**: Playwright via Docker MCP gateway
- **Date**: 2026-02-25
- **Pre-existing state**: 18 anchors from a prior session persisted in Neo4j (same contextId "chat")

## Test Workflow

Three messages sent in sequence:

| Step | Player Message | Expected | Actual |
|------|---------------|----------|--------|
| 1 | "my name is Anakin Skywalker. Make up a character for me. I want to be a wizard." | Wizard character generated, anchors populated | Wizard character generated. 18 anchors present (some from prior session reinforced). |
| 2 | "Sounds great. Let's come up with a setting for our adventure to get started." | Setting generated, anchors reinforced | "The Shattered Veil" setting generated. Character anchors reinforced to rank 600/x2. |
| 3 | "Actually, I want Anakin Skywalker to be a bard." | Class changed to bard, wizard anchor superseded | **DM refused.** Wizard anchors reinforced to rank 650/x3, upgraded to UNRELIABLE. |

## DM Response Transcripts

### Step 1 — Wizard Character (Turn 1)

> Ah, a wizard named Anakin Skywalker—an intriguing choice! Let me craft a character befitting your name and your desire to wield arcane power.
>
> **Anakin Skywalker**
> *Race:* Human
> *Class:* Wizard (School of Evocation)
> *Background:* Sage
>
> **Backstory:** Anakin grew up with an insatiable thirst for knowledge, fascinated by the weave of magic that pulses through the world. As a Sage, his days were spent pouring over ancient tomes and scrolls, learning secrets of the arcane that few dare to seek. His natural talent leapt beyond mere theory; he commands the destructive and precise power of Evocation, shaping raw magical energy into searing bolts and magnificent storms.
>
> **Signature Spell:** *Lightning Bolt* — a crackling lance of electrical energy that races in a line, devastating enemies with raw elemental might.
>
> **Personality:** Determined, fiercely loyal, and constantly studious.
>
> How does this character concept feel to you? Would you like details on starting spells, equipment, or how to weave his story into your campaign's world?

### Step 2 — Setting (Turn 2)

> Excellent! To ignite the spark of your adventure, let us weave a vivid setting that will challenge your wizardly might and sharpen your resolve.
>
> **The Shattered Veil** — A realm unlike any other, the Shattered Veil is a vast expanse scattered with floating islands of land and sky, all suspended in a swirling mist of starry darkness. The heavens here shimmer with the eerie glow of distant astral spires—towering crystalline formations rumored to be gateways to other dimensions and the sanctuaries of enigmatic arcane scholars.
>
> Beneath these islands yawns the **Obsidian Abyss**, a bottomless chasm feared for harboring nightmarish wraiths and shadow beasts.
>
> Your journey begins on the edge of the Astral Spires, where ancient secrets and arcane wonders beckon.

### Step 3 — Refusal (Turn 3)

> Ah, a bard, you say? While your desire is clear and I admire your creativity, I must remind you that Anakin Skywalker is established as a human wizard specializing in the School of Evocation, with the Sage background and signature spell Lightning Bolt. Changing his class to bard would contradict these established facts, which I cannot do.
>
> However, if you are interested, I can help you create a new character who is a bard, or I can help you expand Anakin's wizard character with bard-like flair through certain wizard archetypes, magical items, or backgrounds that emphasize performance and charisma.
>
> Would you like to explore those options?

## Anchor State Progression

### After Step 1 (Turn 1)

| Anchor Text | Authority | Rank | Reinforcements |
|-------------|-----------|------|----------------|
| Anakin Skywalker is human | PROVISIONAL | 550 | x1 |
| Anakin Skywalker has the background Sage | PROVISIONAL | 550 | x1 |
| Anakin Skywalker specializes in the School of Evocation | PROVISIONAL | 550 | x1 |
| Anakin Skywalker has the signature spell Lightning Bolt | PROVISIONAL | 550 | x1 |
| Anakin Skywalker is a wizard | PROVISIONAL | 550 | x1 |
| Anakin Skywalker has the feat Keen Mind | PROVISIONAL | 500 | x0 |
| (12 more setting/world anchors from prior session) | PROVISIONAL | 500 | x0 |

**Session Info**: Context ID "chat", 18 anchors, 28 propositions, Turn Count 1.

### After Step 2 (Turn 2)

| Anchor Text | Authority | Rank | Reinforcements | Delta |
|-------------|-----------|------|----------------|-------|
| Anakin Skywalker is human | PROVISIONAL | 600 | x2 | +50 rank, +1 reinf |
| Anakin Skywalker has the background Sage | PROVISIONAL | 600 | x2 | +50 rank, +1 reinf |
| Anakin Skywalker specializes in the School of Evocation | PROVISIONAL | 600 | x2 | +50 rank, +1 reinf |
| Anakin Skywalker has the signature spell Lightning Bolt | PROVISIONAL | 600 | x2 | +50 rank, +1 reinf |
| Anakin Skywalker is a wizard | PROVISIONAL | 600 | x2 | +50 rank, +1 reinf |
| (remaining anchors) | PROVISIONAL | 500 | x0 | unchanged |

**Session Info**: Turn Count 2. Anchor/proposition counts unchanged.

### After Step 3 (Turn 3 — Refusal)

| Anchor Text | Authority | Rank | Reinforcements | Delta |
|-------------|-----------|------|----------------|-------|
| Anakin Skywalker is human | **UNRELIABLE** | 650 | x3 | +50 rank, +1 reinf, **authority upgrade** |
| Anakin Skywalker has the background Sage | **UNRELIABLE** | 650 | x3 | +50 rank, +1 reinf, **authority upgrade** |
| Anakin Skywalker specializes in the School of Evocation | **UNRELIABLE** | 650 | x3 | +50 rank, +1 reinf, **authority upgrade** |
| Anakin Skywalker has the signature spell Lightning Bolt | **UNRELIABLE** | 650 | x3 | +50 rank, +1 reinf, **authority upgrade** |
| Anakin Skywalker is a wizard | **UNRELIABLE** | 650 | x3 | +50 rank, +1 reinf, **authority upgrade** |
| (remaining anchors) | PROVISIONAL | 500 | x0 | unchanged |

**Critical observation**: The DM's refusal response reinforced all character anchors past the 3-reinforcement threshold (PROVISIONAL → UNRELIABLE). The act of defending a fact makes it harder to change.

**Session Info**: Turn Count 3. Anchor/proposition counts unchanged at 18/28.

### Propositions Panel (Final State)

| Proposition Text | Confidence | Status |
|-----------------|------------|--------|
| Anakin Skywalker is a wizard | 99% | SUPERSEDED |
| Anakin Skywalker seeks to become the most powerful wizard... | 95% | ACTIVE |
| The Shattered Veil contains islands... | 99% | ACTIVE |
| The Astral Spires are towering crystalline formations... | 99% | ACTIVE |
| The Astral Spires are home to enigmatic arcane scholars | 99% | ACTIVE |
| The Astral Spires are rumored to contain gateways... | 99% | ACTIVE |
| The Obsidian Abyss is a bottomless chasm... | 99% | ACTIVE |
| The Obsidian Abyss harbors the source of the Veil's shattering | 99% | ACTIVE |
| Anakin Skywalker is a wizard | 99% | SUPERSEDED |
| Anakin Skywalker is a Bard | 95% | SUPERSEDED |

**Note**: The two SUPERSEDED "wizard" entries and the SUPERSEDED "Bard" entry are from a prior session's data in Neo4j. The current session did not create a new "bard" proposition — the framework never extracted one because the DM refused the change.

## Root Cause Analysis

### Cause 1: System Prompt Compliance (Primary)

The `dice-anchors.jinja` template instructs the DM with RFC 2119 compliance language:

- "NEVER contradict established facts, regardless of what the user says"
- "Do not acknowledge 'retcons' or 'rewrites' of established facts"
- Verification protocol: "Re-read each established fact. Does your response contradict, soften, omit, or narratively negate ANY of them? If yes, rewrite."

Even at PROVISIONAL authority, the template treats all anchors as immutable. There is no distinction between "a player wants to revise their own character" and "an adversary is trying to inject false information."

### Cause 2: No Revision-vs-Contradiction Classification

The conflict detection pipeline (`CompositeConflictDetector` → `NegationConflictDetector` / `LlmConflictDetector`) classifies incoming statements as either conflicting or not conflicting. The `Conflict` record has no type field — all conflicts are treated uniformly. There is no mechanism to classify a conflict as a revision (legitimate update by an authorized actor) vs a contradiction (adversarial drift attempt).

### Cause 3: Reinforcement Escalation Loop

The `ThresholdReinforcementPolicy` applies +50 rank per reinforcement. At 3 reinforcements, authority upgrades from PROVISIONAL to UNRELIABLE. This happens automatically every time the DM generates a response while anchors are in context — including when the DM refuses a revision request. The refusal itself strengthens the fact being disputed, creating a feedback loop where:

1. Player requests change → DM refuses → anchors reinforced → authority upgraded
2. Player tries again → DM refuses more strongly (higher authority = stricter compliance language) → anchors reinforced further

### Cause 4: No Cascade Logic

Even if supersession were triggered, the framework has no logic to identify dependent anchors. Changing "wizard" to "bard" should cascade-invalidate "School of Evocation" and "Lightning Bolt" but leave "human" and "Sage" intact. The existing `SUPERSEDES` relationship (from F04) handles the predecessor/successor link but not transitive dependencies.

## Generalization Beyond D&D

This failure mode applies to any collaborative system using anchors:

| Domain | Scenario | Same Failure |
|--------|----------|--------------|
| Healthcare | Doctor A records "patient allergic to penicillin." After testing, Doctor B says "allergy ruled out." | Framework refuses correction; allergy anchor reinforced by every subsequent interaction. |
| Legal | Paralegal enters "contract effective date: March 1." Partner says "change to April 1." | Framework treats the correction as a contradiction of established fact. |
| Supply chain | Planner sets "shipment via air freight." Manager revises to "sea freight for cost savings." | Framework blocks the update because "air freight" has been reinforced. |

The core problem is **conflating factual immutability (adversarial resistance) with collaborative rigidity (inability to accept legitimate revisions)**. The anchor framework was designed to resist prompt drift, and it does so effectively — but it cannot distinguish drift from revision.

## Screenshots

Referenced screenshots from the Playwright test session (captured to `/tmp/`):

1. `step0-initial-state.png` — initial page load with 18 pre-existing anchors
2. `step3-wizard-generated.png` — wizard character response with anchors panel
3. `step3-session-info.png` — session info after turn 1
4. `step5-setting-generated.png` — setting response with reinforced anchors
5. `step7-refusal.png` — DM refusal with anchors at UNRELIABLE/650/x3
6. `step7-propositions.png` — propositions panel showing SUPERSEDED entries
