## 1. MemoryTier Enum & Configuration

- [x] 1.1 Create `MemoryTier` enum (COLD, WARM, HOT) in `anchor/` package with ordinal ordering [spec: memory-tiering/MemoryTier enum]
- [x] 1.2 Add tier threshold properties to `DiceAnchorsProperties`: `tier.hot-threshold` (default 600), `tier.warm-threshold` (default 350) [spec: memory-tiering/Tier boundary thresholds]
- [x] 1.3 Add tier decay multiplier properties: `tier.hot-decay-multiplier` (1.5), `tier.warm-decay-multiplier` (1.0), `tier.cold-decay-multiplier` (0.6) [spec: tier-aware-decay/Tier-based decay multiplier]
- [x] 1.4 Add startup validation: `hotThreshold > warmThreshold`, both in [100, 900], all multipliers > 0 [spec: memory-tiering/Threshold validation, tier-aware-decay/Decay multiplier validation]
- [x] 1.5 Add tier config block to `application.yml` with defaults [spec: memory-tiering/Tier boundary thresholds]
- [x] 1.6 Write unit tests for `MemoryTier` ordering and tier computation from rank [spec: memory-tiering/Enum ordering, Anchor in HOT/WARM/COLD tier]

## 2. Anchor Record & Persistence

- [x] 2.1 Add `memoryTier` field to `Anchor` record [spec: memory-tiering/Tier on Anchor record]
- [x] 2.2 Add `memoryTier` String property to `PropositionNode` [spec: memory-tiering/Tier persistence]
- [x] 2.3 Update `AnchorRepository` mapping to read/write `memoryTier` from Neo4j [spec: memory-tiering/Tier persistence]
- [x] 2.4 Add startup migration query: set `memoryTier` for existing anchors without it based on rank [spec: memory-tiering/Migration of existing anchors]
- [x] 2.5 Write tests for Anchor record construction with tier, PropositionNode tier mapping [spec: memory-tiering/Tier on Anchor record, Tier persistence]

## 3. AnchorEngine Tier Tracking

- [x] 3.1 Add tier computation helper method to `AnchorEngine` using configured thresholds [spec: memory-tiering/Tier boundary thresholds]
- [x] 3.2 Update `AnchorEngine.promote()` to compute and persist initial tier [spec: memory-tiering/Tier computed on promotion]
- [x] 3.3 Update `AnchorEngine.reinforce()` to recompute tier after rank boost, publish `TierChanged` on transition [spec: memory-tiering/Tier updated on reinforcement, anchor-lifecycle/AnchorEngine tier tracking]
- [x] 3.4 Update `AnchorEngine.applyDecay()` to recompute tier after decay, publish `TierChanged` on transition [spec: memory-tiering/Tier updated on decay, anchor-lifecycle/AnchorEngine tier tracking]
- [x] 3.5 Write unit tests for tier tracking across promote, reinforce, and decay [spec: anchor-lifecycle/Promote with tier tracking, Sequential reinforcements crossing tiers]

## 4. TierChanged Lifecycle Event

- [x] 4.1 Add `TierChanged` record to `AnchorLifecycleEvent` sealed hierarchy with `anchorId`, `previousTier`, `newTier`, `contextId`, `occurredAt` [spec: anchor-lifecycle/TierChanged lifecycle event]
- [x] 4.2 Gate `TierChanged` publishing on `anchorConfig.lifecycleEventsEnabled()` [spec: anchor-lifecycle/TierChanged lifecycle event - Events disabled scenario]
- [x] 4.3 Ensure no `TierChanged` event on initial promotion (not a transition) [spec: anchor-lifecycle/AnchorEngine tier tracking - Promote scenario]
- [x] 4.4 Write unit tests: tier upgrade event, tier downgrade event, no event when tier unchanged, events disabled [spec: anchor-lifecycle/TierChanged lifecycle event scenarios]

## 5. Tier-Aware Decay

- [x] 5.1 Update `ExponentialDecayPolicy` to accept `tierMultiplier` in decay calculation: `effectiveHalfLife = baseHalfLife / max(diceDecay, 0.01) * tierMultiplier` [spec: tier-aware-decay/Tier-based decay multiplier]
- [x] 5.2 Wire `AnchorEngine.applyDecay()` to pass the correct multiplier from tier config [spec: tier-aware-decay/Tier-based decay multiplier]
- [x] 5.3 Write unit tests: HOT decays slower, WARM baseline, COLD decays faster, composition with diceDecay, backward compatibility [spec: tier-aware-decay scenarios]

## 6. Tier-Aware Assembly

- [x] 6.1 Update `PromptBudgetEnforcer` sort comparator: authority > tier DESC > diceImportance DESC > rank DESC [spec: tier-aware-assembly/Tier-aware sort order]
- [x] 6.2 Update `PromptBudgetEnforcer` drop logic: within authority bands, drop COLD first [spec: tier-aware-assembly/Tier-aware budget drop order]
- [x] 6.3 Add tier distribution counts (hotCount, warmCount, coldCount) to `ContextTrace` [spec: tier-aware-assembly/ContextTrace tier metadata]
- [x] 6.4 Write unit tests: HOT preferred over COLD, tier precedence over importance, drop order, CANON immunity preserved [spec: tier-aware-assembly scenarios]

## 7. Observability

- [x] 7.1 Add OTEL span attributes (`anchor.tier`, `anchor.tier.previous`, `anchor.id`) on `TierChanged` events [spec: observability/OTEL span attributes for tier transitions]
- [x] 7.2 Add tier distribution attributes (`anchor.tier.hot_count`, `warm_count`, `cold_count`) to `simulation.turn` spans [spec: observability/Tier distribution in simulation turn spans]
- [x] 7.3 Add INFO-level log on tier transitions when no active span [spec: observability/No active span scenario]

## 8. UI Visibility

- [x] 8.1 Add tier badge to `ContextInspectorPanel` per-anchor row (colored indicator: HOT=red, WARM=amber, COLD=blue)
- [ ] 8.2 Verify tier badge updates during simulation turn execution

## 9. Verification

- [x] 9.1 Run full test suite (`./mvnw.cmd test`) — all existing + new tests pass
- [ ] 9.2 Run application with Neo4j, verify tier field persisted on new anchors
- [ ] 9.3 Run simulation scenario, verify tier transitions visible in ContextInspector and OTEL traces
- [ ] 9.4 Verify backward compatibility: no tier config → all anchors behave as WARM (baseline)
