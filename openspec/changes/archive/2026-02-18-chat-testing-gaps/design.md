## Context

The chat workbench is a testing tool for exploring anchor behavior. Two functional gaps make testing misleading: reinforcement is disconnected from chat, and there's no way to promote extracted propositions without re-creating them manually.

## Goals

- Wire reinforcement so anchors strengthen organically during conversation
- Enable one-click promotion of extracted propositions
- Ensure extraction fires early enough to be useful in short test sessions

## Non-Goals

- Production-quality UX (this is a testing workbench)
- Smart reinforcement detection (which anchors were "referenced" — simple approach: reinforce all active anchors each turn)
- Extraction windowing optimization beyond basic trigger interval tuning

## Decisions

### D1: Reinforce all active anchors each turn

**Options considered:**
1. NLP-based detection of which anchors were referenced in the response
2. Reinforce all active anchors on every turn
3. Reinforce only anchors whose text appears in the response (substring match)

**Chosen: Option 2** — Simple, consistent, and sufficient for testing. The reinforcement policy (`ThresholdReinforcementPolicy`) already controls rank boost magnitude and authority upgrade thresholds, so reinforcing all anchors won't cause runaway rank inflation. This is a testing workbench — precision isn't critical.

### D2: Direct promote via AnchorEngine.promote()

The promote button bypasses `AnchorPromoter.evaluateAndPromote()` (which has confidence gates, conflict checks, and trust evaluation). Manual promotion is an explicit user action — it should work unconditionally, same as the Create Anchor form. Uses `properties.anchor().initialRank()` (default 500) as the starting rank.

### D3: Trigger interval = 2

Lowering from 6 to 2 means extraction fires after the second chat exchange. This is early enough for short test sessions while still batching a couple of turns for better extraction quality.

## Risks

- **Reinforcing all anchors each turn** may cause rank inflation in long sessions. Acceptable for a testing tool; the exponential decay policy provides a natural counterbalance.
- **Trigger interval 2** may increase LLM API costs during testing. Acceptable trade-off for usability.
