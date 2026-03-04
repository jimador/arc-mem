# Implementation Tasks

## 1. AnchorPrologProjector foundation and Prolog rules

- [x] 1.1 Create `prolog/anchor-rules.pl` in `src/main/resources/prolog/`. Define:
  - Negation pairs: `negation(alive, dead)`, `negation(present, absent)`, `negation(true, false)`, `negation(open, closed)`, plus symmetric reverses.
  - Contradiction rule (negation layer): `contradicts(A, B) :- claim(A, S, P, O), claim(B, S, P, O2), negation(O, O2), A \= B.`
  - Authority violation rule: `authority_violation(Id) :- anchor(Id, Auth, _, _, _), authority_floor(Id, MinAuth), Auth < MinAuth.`
  - Eviction violation rule: `eviction_violation(Id) :- anchor(Id, _, _, _, _), eviction_immune(Id).`
  - Contradiction with incoming text rule: `conflicts_with_incoming(ExistingId) :- contradicts(incoming, ExistingId).`

- [x] 1.2 Create `AnchorPrologProjector` in `dev.dunnam.diceanchors.anchor`. Spring `@Service` with constructor injection. Public methods:
  - `PrologEngine project(List<Anchor> anchors)` -- projects anchors to facts, loads rules, returns engine
  - `PrologEngine projectWithIncoming(List<Anchor> anchors, String incomingText)` -- like `project()` but adds a synthetic anchor for incoming text with id `incoming`
  - Private `String buildTheory(List<Anchor> anchors, String additionalFacts)` -- assembles theory string
  - Private `List<String> decomposeText(String anchorId, String text)` -- heuristic SVO extraction producing `claim/4` fact strings
  - Private `String anchorFact(Anchor anchor)` -- produces `anchor/5` fact string

- [x] 1.3 Implement heuristic SVO decomposition in `AnchorPrologProjector.decomposeText()`:
  - Split text on sentence boundaries (`. `, `; `)
  - For each sentence: extract first noun phrase as subject (words before first verb-like word), verb as predicate, remainder as object
  - Normalize: lowercase, replace spaces with underscores, strip non-alphanumeric except underscores
  - Fallback: sentences that don't parse produce `claim(Id, unknown, states, NormalizedFullText)`
  - Atom quoting: wrap all Prolog atoms in single quotes to handle special characters

- [x] 1.4 Implement `project()` and `projectWithIncoming()`:
  - Generate `anchor/5` facts for each anchor
  - Generate `claim/4` facts via `decomposeText()` for each anchor
  - Load rules via `PrologRuleLoader.INSTANCE.loadFromResource("prolog/anchor-rules.pl")`
  - Concatenate: facts + rules = theory string
  - Return `PrologEngine.Companion.fromTheory(theory)` (Kotlin companion factory, callable from Java)
  - Log at INFO: anchor count, fact count, theory size

**Verification**: `./mvnw clean compile -DskipTests`

## 2. PrologConflictDetector -- LOGICAL strategy implementation

- [x] 2.1 Create `PrologConflictDetector` in `dev.dunnam.diceanchors.anchor`. Implements `ConflictDetector`. Constructor takes `AnchorPrologProjector`.

- [x] 2.2 Implement `detect(String incomingText, List<Anchor> existingAnchors)`:
  - Call `projector.projectWithIncoming(existingAnchors, incomingText)`
  - For each existing anchor, query `conflicts_with_incoming('id')` (ground query) to find conflicts
  - Build `Conflict` record: existing anchor, incoming text, confidence 1.0 (Prolog is deterministic), reason "Prolog logical contradiction", `DetectionQuality.FULL`, `ConflictType.CONTRADICTION`
  - Wrap in try-catch: on any exception, log WARN and return empty list
  - Log at INFO: incoming text (truncated), existing anchor count, conflicts found, query time ms

- [x] 2.3 Update `CompositeConflictDetector`:
  - Add `@Nullable PrologConflictDetector logicalDetector` as a constructor parameter (6th parameter). Add backward-compatible 5-parameter constructor that passes null.
  - Replace LOGICAL branch in `detect()`: if `logicalDetector != null`, delegate; else throw `UnsupportedOperationException`
  - Replace LOGICAL branch in `batchDetect()`: if `logicalDetector != null`, delegate to default `batchDetect()` on `logicalDetector`; else throw

- [x] 2.4 Add `LOGICAL` to `ConflictStrategy` enum.

- [x] 2.5 Add `LOGICAL` case to `AnchorConfiguration.conflictDetector()`:
  - Create `AnchorPrologProjector` bean (or inject if already wired)
  - Create `PrologConflictDetector` using the projector
  - Wire `CompositeConflictDetector` with `ConflictDetectionStrategy.LOGICAL`, the Prolog detector, plus lexical/semantic detectors for the other branches
  - Log at INFO: "Using LOGICAL (Prolog) conflict detection"

**Verification**: `./mvnw clean compile -DskipTests`

## 3. PrologAuditPreFilter -- maintenance audit integration

- [x] 3.1 Create `PrologAuditPreFilter` in `dev.dunnam.diceanchors.anchor`. Constructor takes `AnchorPrologProjector`.

- [x] 3.2 Implement `Set<String> flagContradictingAnchors(List<Anchor> anchors)`:
  - Call `projector.project(anchors)`
  - For each anchor pair (i, j), query `contradicts('id_i', 'id_j')` (ground queries) to find contradiction pairs
  - Collect unique anchor IDs from all matching pairs
  - Wrap in try-catch: on any exception, log WARN and return empty set
  - Log at INFO: anchor count, contradiction pairs found, flagged anchor count, query time ms
  - Return the set of flagged anchor IDs

- [x] 3.3 Add `prologPreFilterEnabled` to `DiceAnchorsProperties.ProactiveConfig`:
  - Type: `boolean`, default: `false`
  - Add as last parameter with `@DefaultValue("false")`

- [x] 3.4 Integrate pre-filter in `ProactiveMaintenanceStrategy.auditAnchors()`:
  - Accept `PrologAuditPreFilter` as an optional (`@Nullable`) constructor parameter
  - Before heuristic scoring loop, if `config.prologPreFilterEnabled()` and `preFilter != null`:
    - Call `preFilter.flagContradictingAnchors(anchors)`
    - For flagged anchors, set heuristic score to 0.0 directly
  - Non-flagged anchors proceed to normal `computeHeuristicScore()`

- [x] 3.5 Wire `PrologAuditPreFilter` bean in `AnchorConfiguration`:
  - `@Bean @ConditionalOnMissingBean PrologAuditPreFilter prologAuditPreFilter(AnchorPrologProjector projector)`
  - Pass to `ProactiveMaintenanceStrategy` constructor

**Verification**: `./mvnw clean compile -DskipTests`

## 4. PrologInvariantEnforcer -- compliance enforcement

- [x] 4.1 Create `PrologInvariantEnforcer` in `dev.dunnam.diceanchors.assembly`. Implements `ComplianceEnforcer`. Constructor takes `AnchorPrologProjector`.

- [x] 4.2 Implement `ComplianceResult enforce(ComplianceContext context)`:
  - Filter `context.activeAnchors()` by `context.policy()` authority tiers
  - If no enforced anchors, return `ComplianceResult.compliant(Duration.ZERO)`
  - Call `projector.projectWithIncoming(enforcedAnchors, context.responseText())`
  - For each enforced anchor, query `conflicts_with_incoming('id')` (ground query) to find contradictions
  - For each match, build `ComplianceViolation`: anchor id, anchor text, anchor authority, description "Prolog detected contradiction with response", confidence 1.0
  - Determine `ComplianceAction`: REJECT if any violation involves CANON or RELIABLE; RETRY if only UNRELIABLE or PROVISIONAL; ACCEPT if no violations
  - Wrap in try-catch: on any exception, log WARN and return `ComplianceResult.compliant()` (fail-open)
  - Log at INFO: enforced anchor count, violations found, action, timing

- [x] 4.3 Wire `PrologInvariantEnforcer` bean in `AnchorConfiguration` (or a new `PrologConfiguration`):
  - `@Bean PrologInvariantEnforcer prologInvariantEnforcer(AnchorPrologProjector projector)`
  - The enforcer is available as an alternative `ComplianceEnforcer` -- callers select it explicitly (not auto-wired as primary)

**Verification**: `./mvnw clean compile -DskipTests`

## 5. Unit tests for critical components

- [x] 5.1 Create `AnchorPrologProjectorTest` in `anchor/` test package:
  - Test projection produces `anchor/5` facts for each anchor
  - Test projection produces `claim/4` facts from anchor text
  - Test empty anchor list produces valid engine
  - Test SVO decomposition for simple sentences ("X is Y", "X has Y")
  - Test fallback for unparseable text
  - Test `projectWithIncoming()` adds synthetic incoming anchor
  - Test detects negation contradiction via ground query

- [x] 5.2 Create `PrologConflictDetectorTest` in `anchor/` test package:
  - Test detects negation contradiction ("X is alive" vs "X is dead")
  - Test returns empty for non-contradicting anchors
  - Test returns empty list on projection failure (mock projector to throw)
  - Test handles empty anchor list

- [x] 5.3 Create `PrologAuditPreFilterTest` in `anchor/` test package:
  - Test flags contradicting anchor pair
  - Test returns empty set when no contradictions
  - Test returns empty set on failure

- [x] 5.4 Create `PrologInvariantEnforcerTest` in `assembly/` test package:
  - Test detects contradiction with CANON anchor, returns REJECT
  - Test returns compliant for non-contradicting response
  - Test respects CompliancePolicy filter (canonOnly excludes PROVISIONAL)
  - Test returns compliant on failure (fail-open)

**Verification**: `./mvnw test`

## 6. Integration wiring and CompositeConflictDetector LOGICAL branch test

- [x] 6.1 Add test in existing `CompositeConflictDetectorTest`:
  - Test LOGICAL strategy delegates to `PrologConflictDetector` when injected
  - Test LOGICAL strategy throws `UnsupportedOperationException` when `logicalDetector` is null
  - Test LOGICAL strategy throws `UnsupportedOperationException` on `batchDetect` when null

- [x] 6.2 Verify full test suite passes: `./mvnw test`

**Verification**: `./mvnw test`

## Definition of Done

- [x] All compilation succeeds: `./mvnw clean compile -DskipTests`
- [x] All tests pass: `./mvnw test`
- [x] `CompositeConflictDetector` LOGICAL branch delegates to `PrologConflictDetector` (no more `UnsupportedOperationException` when detector is injected)
- [x] `ConflictStrategy` has 5 values: LEXICAL, HYBRID, LLM, INDEXED, LOGICAL
- [x] `AnchorPrologProjector` projects anchors to `PrologEngine` with hybrid fact schema
- [x] `PrologConflictDetector` detects negation contradictions deterministically
- [x] `PrologAuditPreFilter` flags contradicting anchors in maintenance audit
- [x] `PrologInvariantEnforcer` provides deterministic compliance enforcement
- [x] All Prolog rules defined in `prolog/anchor-rules.pl` (not hardcoded Java)
- [x] All Prolog implementations return safe defaults on failure (never throw)
- [x] Zero new dependencies -- all Prolog via DICE 0.1.0-SNAPSHOT tuProlog
- [x] No anchor state modifications in Prolog code (invariant PI7)
- [x] Prolog projection and query events logged at INFO level
