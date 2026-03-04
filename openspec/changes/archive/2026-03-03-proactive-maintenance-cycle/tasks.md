# Implementation Tasks

## Phase 1: Configuration and Supporting Types

### 1. Add ProactiveConfig and supporting records
**Spec refs**: Configurable sweep parameters, 5-step sweep execution
**Files**: `DiceAnchorsProperties.java`, `anchor/AuditScore.java`, `anchor/SweepType.java`, `anchor/CycleMetrics.java`

- [x] 1.1 Add `ProactiveConfig` nested record to `DiceAnchorsProperties.MaintenanceConfig` with Jakarta validation annotations:
  - `minTurnsBetweenSweeps` (int, default 10, @Min(1))
  - `hardPruneThreshold` (double, default 0.1, @DecimalMin("0.0") @DecimalMax("1.0"))
  - `softPruneThreshold` (double, default 0.3, @DecimalMin("0.0") @DecimalMax("1.0"))
  - `softPrunePressureThreshold` (double, default 0.6, @DecimalMin("0.0") @DecimalMax("1.0"))
  - `candidacyMinReinforcements` (int, default 10, @Min(1))
  - `candidacyMinAuditScore` (double, default 0.8, @DecimalMin("0.0") @DecimalMax("1.0"))
  - `candidacyMinAge` (int, default 5, @Min(1))
  - `rankBoostAmount` (int, default 50, @Min(1) @Max(200))
  - `rankPenaltyAmount` (int, default 50, @Min(1) @Max(200))
  - `llmAuditEnabled` (boolean, default false)
  - Validation: `@AssertTrue` that `hardPruneThreshold < softPruneThreshold`
- [x] 1.2 Update `MaintenanceConfig` to add `@Valid @NestedConfigurationProperty ProactiveConfig proactive` field
- [x] 1.3 Create `AuditScore` record in `anchor/` package: `anchorId`, `heuristicScore`, `finalScore`, `llmRefined`
- [x] 1.4 Create `SweepType` enum in `anchor/` package: `LIGHT`, `FULL`, `NONE`
- [x] 1.5 Create `CycleMetrics` record in `anchor/` package: `anchorsAudited`, `anchorsRefreshed`, `anchorsConsolidated`, `anchorsPruned`, `validationViolations`, `sweepType`, `duration`
- [x] 1.6 Add default proactive config values to `application.yml` under `dice-anchors.maintenance.proactive`

**Verify**: `./mvnw clean compile -DskipTests` passes

## Phase 2: ProactiveMaintenanceStrategy Skeleton + Trigger Logic

### 2. Implement shouldRunSweep and sweep type determination
**Spec refs**: Pressure-triggered activation, Error resilience
**Files**: `ProactiveMaintenanceStrategy.java`, `AnchorConfiguration.java`

- [x] 2.1 Add constructor dependencies to `ProactiveMaintenanceStrategy`: `MemoryPressureGauge`, `AnchorEngine`, `AnchorRepository`, `CanonizationGate`, `InvariantEvaluator`, `LlmCallService`, `DiceAnchorsProperties`
- [x] 2.2 Add `ConcurrentHashMap<String, Integer>` for tracking `lastSweepTurn` per contextId
- [x] 2.3 Implement `shouldRunSweep(MaintenanceContext)`:
  - Compute pressure via `pressureGauge.computePressure(contextId, anchorCount, budget)`
  - Check `pressure.total() >= lightSweepThreshold`
  - Check turn interval: `turnNumber - lastSweepTurn >= minTurnsBetweenSweeps`
  - Return `true` only if both conditions met
  - Wrap in try-catch, return `false` on error (per error contract)
- [x] 2.4 Add private `determineSweepType(PressureScore)` method returning `SweepType`
- [x] 2.5 Implement `executeSweep(MaintenanceContext)` skeleton:
  - Determine sweep type
  - Call each step in order with try-catch per step
  - Record `lastSweepTurn`
  - Build and return `SweepResult`
  - Log cycle summary at INFO level
- [x] 2.6 Update `AnchorConfiguration.maintenanceStrategy()` bean to pass new dependencies to `ProactiveMaintenanceStrategy` constructor

**Verify**: `./mvnw clean compile -DskipTests` passes

### 3. Tests for trigger logic
**Spec refs**: Pressure-triggered activation
**Files**: `ProactiveMaintenanceStrategyTest.java`

- [x] 3.1 Test `shouldRunSweep` returns `true` when pressure >= threshold AND min turns elapsed
- [x] 3.2 Test `shouldRunSweep` returns `false` when pressure < threshold
- [x] 3.3 Test `shouldRunSweep` returns `false` when min turns not elapsed
- [x] 3.4 Test `determineSweepType` returns `FULL` for pressure >= 0.8
- [x] 3.5 Test `determineSweepType` returns `LIGHT` for pressure between 0.4 and 0.8
- [x] 3.6 Test `determineSweepType` returns `NONE` for pressure < 0.4
- [x] 3.7 Test `shouldRunSweep` catches exceptions and returns `false`

**Verify**: `./mvnw test` -- new tests pass

## Phase 3: Audit Step

### 4. Implement heuristic audit scoring
**Spec refs**: Audit step -- heuristic relevance scoring
**Files**: `ProactiveMaintenanceStrategy.java`

- [x] 4.1 Add private `auditAnchors(MaintenanceContext, SweepType)` method returning `List<AuditScore>`
- [x] 4.2 Implement heuristic scoring with three signals:
  - Recency: `1.0 - min(turnsSinceReinforcement / maxTurns, 1.0)` -- approximate `turnsSinceReinforcement` from `context.turnNumber()` and anchor metadata
  - Rank position: `(rank - MIN_RANK) / (MAX_RANK - MIN_RANK)`
  - Memory tier: HOT=1.0, WARM=0.5, COLD=0.2
  - Combined: `(recency * 0.33) + (rankPosition * 0.33) + (tierScore * 0.34)`
- [x] 4.3 For `SweepType.FULL` with `llmAuditEnabled`: identify borderline anchors (score between softPruneThreshold and 0.7) and invoke batched LLM call via `LlmCallService`
- [x] 4.4 Add audit prompt template to `src/main/resources/prompts/audit-relevance.st`
- [x] 4.5 Handle LLM call failure: log at WARN, retain heuristic scores

### 5. Tests for audit step
**Spec refs**: Audit step -- heuristic relevance scoring, optional batched LLM evaluation
**Files**: `ProactiveMaintenanceStrategyTest.java`

- [x] 5.1 Test high-rank HOT anchor with recent reinforcement scores high (>= 0.7)
- [x] 5.2 Test low-rank COLD anchor with no recent reinforcement scores low (<= 0.3)
- [x] 5.3 Test LLM audit refinement replaces heuristic scores for borderline anchors
- [x] 5.4 Test LLM failure falls back to heuristic scores
- [x] 5.5 Test light sweep skips LLM call entirely

**Verify**: `./mvnw test` -- new tests pass

## Phase 4: Refresh Step

### 6. Implement refresh step
**Spec refs**: Refresh step -- re-rank based on audit scores, trust re-evaluation
**Files**: `ProactiveMaintenanceStrategy.java`

- [x] 6.1 Add private `refreshAnchors(List<AuditScore>, MaintenanceContext)` method returning count of refreshed anchors
- [x] 6.2 For score >= 0.7: boost rank by `rankBoostAmount` via `repository.updateRank(id, clampRank(rank + boost))`
- [x] 6.3 For score <= 0.3: penalize rank by `rankPenaltyAmount` via `repository.updateRank(id, clampRank(rank - penalty))`
- [x] 6.4 Skip CANON and pinned anchors for rank penalty (immunity)
- [x] 6.5 For score between 0.2 and 0.4: trigger trust re-evaluation via `AnchorEngine`
- [x] 6.6 Mid-range anchors (0.3 to 0.7): no rank change

### 7. Tests for refresh step
**Spec refs**: Refresh step
**Files**: `ProactiveMaintenanceStrategyTest.java`

- [x] 7.1 Test high-score anchor receives rank boost
- [x] 7.2 Test low-score anchor receives rank penalty
- [x] 7.3 Test borderline anchor rank unchanged
- [x] 7.4 Test CANON anchor immune to rank penalty
- [x] 7.5 Test pinned anchor immune to rank penalty
- [x] 7.6 Test rank boost clamped at MAX_RANK
- [x] 7.7 Test rank penalty clamped at MIN_RANK
- [x] 7.8 Test borderline anchor triggers trust re-evaluation

**Verify**: `./mvnw test` -- new tests pass

## Phase 5: Consolidate Step

### 8. Implement consolidate step
**Spec refs**: Consolidate step -- CANON candidacy routing
**Files**: `ProactiveMaintenanceStrategy.java`

- [x] 8.1 Add private `consolidateAnchors(List<AuditScore>, MaintenanceContext)` method returning count of candidates routed
- [x] 8.2 Filter to RELIABLE anchors only
- [x] 8.3 Check candidacy criteria: reinforcementCount >= min, auditScore >= min, age >= min (approximate via `reinforcementCount / 2`)
- [x] 8.4 Route candidates to `CanonizationGate.requestCanonization(id, contextId, text, authority, reason, "proactive-maintenance")`
- [x] 8.5 Log each candidacy routing at DEBUG level

### 9. Tests for consolidate step
**Spec refs**: Consolidate step
**Files**: `ProactiveMaintenanceStrategyTest.java`

- [x] 9.1 Test RELIABLE anchor meeting all criteria is routed to CanonizationGate
- [x] 9.2 Test RELIABLE anchor missing reinforcement count criterion is not routed
- [x] 9.3 Test RELIABLE anchor missing audit score criterion is not routed
- [x] 9.4 Test RELIABLE anchor missing age criterion is not routed
- [x] 9.5 Test PROVISIONAL/UNRELIABLE anchors are skipped entirely

**Verify**: `./mvnw test` -- new tests pass

## Phase 6: Prune Step

### 10. Implement prune step
**Spec refs**: Prune step -- two-tier threshold eviction, CANON and pinned immunity
**Files**: `ProactiveMaintenanceStrategy.java`

- [x] 10.1 Add private `pruneAnchors(List<AuditScore>, MaintenanceContext, PressureScore)` method returning list of pruned anchor IDs
- [x] 10.2 Skip CANON and pinned anchors (invariants A3b, A3d)
- [x] 10.3 Archive anchors with score < `hardPruneThreshold` unconditionally
- [x] 10.4 Archive anchors with score < `softPruneThreshold` when `pressure.total() >= softPrunePressureThreshold`
- [x] 10.5 Use `AnchorEngine.archiveAnchor()` or equivalent repository method for archival
- [x] 10.6 Log each pruned anchor at DEBUG level

### 11. Tests for prune step
**Spec refs**: Prune step, CANON and pinned immunity
**Files**: `ProactiveMaintenanceStrategyTest.java`

- [x] 11.1 Test anchor below hard floor is always pruned
- [x] 11.2 Test anchor below soft floor pruned when pressure >= threshold
- [x] 11.3 Test anchor below soft floor retained when pressure < threshold
- [x] 11.4 Test anchor above soft floor is never pruned
- [x] 11.5 Test CANON anchor immune to pruning regardless of score
- [x] 11.6 Test pinned anchor immune to pruning regardless of score

**Verify**: `./mvnw test` -- new tests pass

## Phase 7: Validate Step

### 12. Implement validate step
**Spec refs**: Validate step -- invariant and compaction checks
**Files**: `ProactiveMaintenanceStrategy.java`

- [x] 12.1 Add private `validateSweep(MaintenanceContext, List<String> prunedIds, List<Anchor> remainingAnchors)` method returning violation count
- [x] 12.2 For each pruned anchor: call `InvariantEvaluator.evaluate(contextId, ARCHIVE, remainingAnchors, prunedAnchor)`
- [x] 12.3 Identify protected anchors (CANON + pinned) from remaining set
- [x] 12.4 Run `CompactionValidator.validate()` with joined anchor texts as summary and protected anchors
- [x] 12.5 Log invariant violations at WARN level
- [x] 12.6 Log compaction losses at WARN level
- [x] 12.7 Return total violation count

### 13. Tests for validate step
**Spec refs**: Validate step
**Files**: `ProactiveMaintenanceStrategyTest.java`

- [x] 13.1 Test clean validation returns zero violations
- [x] 13.2 Test invariant violation detected and logged
- [x] 13.3 Test compaction loss detected and logged
- [x] 13.4 Test violation count included in SweepResult

**Verify**: `./mvnw test` -- new tests pass

## Phase 8: Integration and End-to-End

### 14. Wire full cycle and add integration tests
**Spec refs**: 5-step sweep execution, Cycle metrics tracking, Error resilience
**Files**: `ProactiveMaintenanceStrategy.java`, `ProactiveMaintenanceStrategyTest.java`

- [x] 14.1 Integrate all 5 steps into `executeSweep()` method with per-step error handling
- [x] 14.2 Build `SweepResult` from accumulated metrics
- [x] 14.3 Log cycle summary at INFO: `"Proactive sweep complete: type={} audited={} refreshed={} consolidated={} pruned={} violations={} duration={}ms"`
- [x] 14.4 Test full sweep executes all 5 steps with realistic mock data
- [x] 14.5 Test light sweep uses heuristic-only audit
- [x] 14.6 Test error in one step does not prevent subsequent steps
- [x] 14.7 Test SweepResult contains correct metrics after full cycle
- [x] 14.8 Run full test suite: `./mvnw test` -- all tests pass, no regressions

**Verify**: `./mvnw test` -- full suite passes

## Definition of Done

- ProactiveMaintenanceStrategy executes 5-step sweep (audit, refresh, consolidate, prune, validate)
- Pressure-triggered activation via MemoryPressureGauge (light at 0.4, full at 0.8)
- Heuristic audit scoring with optional LLM refinement for full sweeps
- Two-tier pruning (hard floor 0.1, soft floor 0.3)
- CANON and pinned anchors immune to pruning and rank penalty
- CANON candidacy routed through CanonizationGate
- Post-sweep validation via CompactionValidator + InvariantEvaluator
- All sweep parameters configurable via DiceAnchorsProperties
- Cycle summary logged at INFO level
- Error resilience: no exceptions propagated from executeSweep()
- All tests pass including new unit tests for each step
