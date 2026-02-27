# Research Task: Cross-Domain Prior Art for Authoritative State Revision

## Task ID

`R05`

## Question

What established solutions exist in non-AI domains for maintaining authoritative state while allowing legitimate collaborative revisions — and which patterns are transferable to anchor mutation?

## Why This Matters

1. Decision blocked by uncertainty: The revision-vs-contradiction problem is not unique to AI memory. Multiple mature disciplines have wrestled with "how do you let authorized actors change established facts without opening the door to unauthorized corruption?" Understanding these solutions may reveal proven patterns we're unaware of.
2. Potential impact if wrong: Designing from first principles when decades of prior art exists risks inferior solutions and missed edge cases that other domains have already encountered and resolved.
3. Related feature IDs: F01 (classification), F02 (compliance rules), F03 (cascade), F04 (provenance).

## Scope

### In Scope

Survey at minimum the following domains and their analogous mechanisms. For each, capture: the core problem analogy, the solution pattern, and transferability to anchor mutation.

#### 1. Truth Maintenance Systems (TMS) — AI/Knowledge Representation
- **Analogy**: TMS tracks logical dependencies between beliefs. When a base belief is retracted, derived beliefs are automatically retracted. This is EXACTLY the dangling proposition/cascade problem.
- **Key variants**: JTMS (Justification-based), ATMS (Assumption-based), dependency-directed backtracking.
- **Research focus**: How does JTMS determine which beliefs to retract? What's the dependency representation? How does it handle circular dependencies?
- **Transferability**: High for F03 (cascade). JTMS justification records map directly to `DERIVED_FROM` relationships.

#### 2. AGM Belief Revision — Epistemology/Logic
- **Analogy**: The AGM framework (Alchourrón, Gärdenfors, Makinson 1985) defines three operations on belief sets: expansion (add new belief), contraction (remove belief), and revision (add belief that contradicts existing beliefs). Revision = contraction + expansion. This is the formal theory of what we're trying to implement.
- **Key concepts**: Entrenchment ordering (analogous to anchor rank), minimal change principle, recovery postulate.
- **Research focus**: How does entrenchment ordering decide which beliefs to give up during revision? How does the minimal change principle limit cascade scope?
- **Transferability**: High for F01 and F02. Entrenchment ordering is essentially our rank/authority model. The AGM postulates could validate our revision rules.

#### 3. MVCC (Multi-Version Concurrency Control) — Database Systems
- **Analogy**: MVCC (PostgreSQL, Oracle) maintains multiple versions of data concurrently. Readers see a consistent snapshot; writers create new versions. "Read committed" vs "serializable" isolation levels control when changes become visible.
- **Key concepts**: Transaction isolation, write intent locks, snapshot isolation, version chains.
- **Research focus**: How does MVCC distinguish between a legitimate UPDATE and a conflicting concurrent write? How does it handle dependent rows (foreign keys) during updates?
- **Transferability**: Medium for F04 (provenance/versioning). The bi-temporal model we already have is inspired by database temporal tables. MVCC's "write intent" concept maps to revision intent.

#### 4. CRDTs (Conflict-free Replicated Data Types) — Distributed Systems
- **Analogy**: CRDTs allow concurrent updates to replicated data without coordination. Last-Writer-Wins registers, OR-Sets, etc. They mathematically guarantee convergence.
- **Key concepts**: Commutative/convergent operations, vector clocks, causal ordering, merge semantics.
- **Research focus**: How do LWW-Registers handle the case where two actors update the same value? Is this always "last writer wins" or are there authority-aware variants? How do OR-Sets handle remove-after-add conflicts?
- **Transferability**: Medium for multi-actor scenarios. LWW-Register with authority-weighted timestamps is a possible resolution model.

#### 5. Engineering Change Orders (ECO) — Manufacturing/Supply Chain
- **Analogy**: When a component specification changes in manufacturing, an ECO is issued. It triggers cascade updates to Bills of Materials (BOMs), assembly instructions, quality specs, and supplier contracts. The ECO process formally distinguishes between revisions (authorized design changes) and nonconformances (errors to be corrected).
- **Key concepts**: Revision levels, effectivity dates, interchangeability analysis, impact assessment matrices.
- **Research focus**: How does the ECO process determine which downstream assemblies are affected by a component change? How does interchangeability analysis decide what can stay vs what must change?
- **Transferability**: High for F03 (cascade). BOM dependency trees are a direct analogue to anchor dependency graphs. Impact assessment matrices are a proven cascade evaluation technique.

#### 6. Medical Records — Healthcare Informatics
- **Analogy**: HIPAA requires that medical records cannot be deleted, but they can be amended. An amendment supersedes the original entry while preserving it for audit. The distinction between amendment (correction of fact), addendum (new information), and late entry (retroactive documentation) maps to REVISION/WORLD_PROGRESSION/NEW_FACT.
- **Key concepts**: Amendment vs correction vs addendum; original entry preservation; author attribution; time-stamped audit trail.
- **Research focus**: How does the amendment process distinguish authorized correction from unauthorized modification? How are dependent clinical decisions (prescriptions based on the amended diagnosis) handled?
- **Transferability**: High for F01 (classification) and F04 (provenance). The amendment/addendum/correction taxonomy is a battle-tested classification scheme.

#### 7. Legal Precedent / Case Law — Jurisprudence
- **Analogy**: Courts distinguish between extending precedent (world progression), distinguishing a case (not applicable — analogous to NOT_MENTIONED), and overruling (supersession). The authority hierarchy (trial → appellate → supreme) maps to our PROVISIONAL → RELIABLE → CANON model.
- **Key concepts**: Stare decisis, overruling, distinguishing, ratio decidendi vs obiter dicta, hierarchy of authority.
- **Research focus**: How does the legal system determine when precedent should be overruled vs distinguished? What's the "cascade" when a supreme court overrules — how are downstream cases affected?
- **Transferability**: Medium for F01 (authority-aware classification) and F02 (compliance rules). The ratio/obiter distinction (core holding vs tangential commentary) maps to essential vs dependent anchors.

#### 8. Wikipedia Edit Classification — Collaborative Knowledge Management
- **Analogy**: Wikipedia's automated systems (ClueBot NG, ORES) classify edits as constructive, vandalism, or revert-worthy. This is literally revision-vs-contradiction classification at scale, with years of empirical tuning.
- **Key concepts**: Edit quality prediction, feature engineering for vandalism detection, revert detection, good-faith vs bad-faith edits.
- **Research focus**: What features do vandalism classifiers use? How accurate are they? What's the false-positive rate for legitimate edits flagged as vandalism? How does Wikipedia handle cascade effects of reverted edits?
- **Transferability**: High for F01 (classification). Wikipedia's edit classifier features (user tenure, edit size, content similarity, lexical signals) may inform our revision-vs-contradiction prompt engineering.

#### 9. Accounting — Double-Entry Bookkeeping
- **Analogy**: Journal entries are immutable once posted. Corrections are made via reversing entries (debit what was credited, credit what was debited) that explicitly supersede the original. The correction + new entry pattern is a formalized supersession model.
- **Key concepts**: Reversing entries, adjusting entries, audit trail, materiality threshold.
- **Research focus**: How does the materiality threshold work — when is a correction significant enough to require a formal reversing entry vs a minor adjustment? How does this map to authority-level revision eligibility?
- **Transferability**: Medium for F02 (compliance rules). The materiality concept could inform our authority-level gating (PROVISIONAL corrections are trivial; RELIABLE corrections are material and require documentation).

### Out of Scope

1. Deep academic review of any single domain (this is a survey, not a thesis).
2. Implementation of patterns from other domains (deferred to feature proposals).
3. Domains where the analogy is too loose to be actionable.

## Research Criteria

1. Required channels: `web`, `repo-docs`
2. Source priority order: academic papers/textbooks > official documentation > practitioner blogs
3. Freshness window: No limit — some of these domains have foundational literature from the 1980s-2000s (TMS, AGM, MVCC) that remains the definitive reference.
4. Minimum evidence count: 5 (at least 4 distinct domains analyzed with concrete pattern extraction)
5. Timebox: 12h
6. Target confidence: `medium`

## Method

1. Local code/doc checks: Review existing bi-temporal validity spec (inspired by database temporal tables — a cross-domain pattern already applied).
2. External evidence collection:
   - **TMS**: Doyle 1979 (JTMS), de Kleer 1986 (ATMS) — foundational papers
   - **AGM**: Alchourrón, Gärdenfors, Makinson 1985 — "On the Logic of Theory Change"
   - **MVCC**: PostgreSQL documentation on MVCC internals; Berenson et al. "A Critique of ANSI SQL Isolation Levels"
   - **CRDTs**: Shapiro et al. 2011 — "A Comprehensive Study of CRDTs"
   - **ECO/BOM**: PLM (Product Lifecycle Management) documentation; APICS/ASCM standards
   - **Medical records**: HIPAA amendment regulations (45 CFR 164.526); HL7 FHIR Provenance resource
   - **Legal**: Jurisprudence textbooks on stare decisis; overruling patterns
   - **Wikipedia**: Adler et al. "Wikipedia Vandalism Detection" (2011); ORES documentation
   - **Accounting**: GAAP/IFRS standards on error correction (IAS 8)
3. Pattern extraction: For each domain, produce a standardized pattern card: {problem, mechanism, classification taxonomy, cascade handling, authority model, transferability assessment}.

## Findings

| Evidence ID | Channel | Source | Date Captured | Key Evidence | Reliability |
|-------------|---------|--------|---------------|--------------|-------------|
| E01 | literature | Doyle, J. "A Truth Maintenance System." *Artificial Intelligence* 12(3), 1979, pp. 231-272 | 2026-02-25 | JTMS: Each belief has a justification record (set of supporting beliefs). Label propagation algorithm marks beliefs IN or OUT. Retraction of a support triggers recursive OUT-labeling of all dependents. Dependency-directed backtracking identifies minimal retraction sets. | high (foundational paper, universally cited) |
| E02 | literature | de Kleer, J. "An Assumption-based TMS." *Artificial Intelligence* 28(2), 1986, pp. 127-162 | 2026-02-25 | ATMS: Maintains ALL consistent environments simultaneously (not just one current label set). Each node has a label = set of minimal environments under which it holds. Retraction = removing an assumption eliminates all environments containing it. No backtracking needed; all alternatives pre-computed. | high (foundational paper) |
| E03 | literature | Alchourrón, C., Gärdenfors, P., Makinson, D. "On the Logic of Theory Change: Partial Meet Contraction and Revision Functions." *J. Symbolic Logic* 50(2), 1985, pp. 510-530 | 2026-02-25 | AGM postulates define rational belief revision. Levi identity: revision K*p = (K - not-p) + p. Entrenchment ordering determines retraction priority. Minimal change principle: remove only what is necessary to accommodate the new belief. | high (foundational paper, defines the field) |
| E04 | literature | Gärdenfors, P. *Knowledge in Flux: Modeling the Dynamics of Epistemic States.* MIT Press, 1988 | 2026-02-25 | Extends AGM with epistemic entrenchment: beliefs ranked by how firmly held. During contraction, least-entrenched beliefs retracted first. Entrenchment must satisfy: if A is less entrenched than B, then when forced to give up one, A goes first. | high (seminal textbook) |
| E05 | documentation | PostgreSQL documentation, Chapter 13: Concurrency Control (MVCC) | 2026-02-25 | PostgreSQL MVCC: each row version (tuple) has xmin (creating txn) and xmax (deleting txn). UPDATE = mark old tuple dead (set xmax) + insert new tuple. Snapshot determines visibility: tuple visible if xmin committed and xmax not yet committed at snapshot time. Write-write conflicts detected by checking if xmax already set by concurrent txn. | high (official documentation) |
| E06 | literature | Berenson, H. et al. "A Critique of ANSI SQL Isolation Levels." *SIGMOD Record* 24(2), 1995, pp. 1-10 | 2026-02-25 | Defines write-write conflict precisely: two concurrent transactions updating the same row. Under snapshot isolation, first committer wins; second aborts. This is the "write skew" problem. Distinguishes phenomena: dirty write, dirty read, non-repeatable read, phantom. | high (seminal paper on isolation) |
| E07 | literature | Shapiro, M., Preguiça, N., Baquero, C., Zawirski, M. "A Comprehensive Study of Convergent and Commutative Replicated Data Types." INRIA Research Report RR-7506, 2011 | 2026-02-25 | CRDTs guarantee convergence without coordination. LWW-Register: last timestamp wins, ties broken by replica ID. OR-Set: add wins over concurrent remove (add-wins semantics). Multi-Value Register: preserves all concurrent writes, leaves merge to application. | high (foundational survey, 3000+ citations) |
| E08 | standards | CMII Standard for Configuration Management (Institute of Configuration Management); ISO 10007:2017 | 2026-02-25 | ECO classification: Class I (form/fit/function change — requires full impact assessment), Class II (documentation-only, no interchangeability impact). Impact assessment matrix cross-references changed item against all parent assemblies in BOM. Effectivity dates control when change takes effect. | high (industry standard) |
| E09 | regulation | HIPAA Privacy Rule, 45 CFR 164.526 — Amendment of Protected Health Information | 2026-02-25 | Patients have right to amend PHI. Amendment appended; original not altered. Provider may deny if original is accurate. Amendment must include: identification of affected records, reason, date, identity of requestor. Linked amendments: if PHI was disclosed to others, they must be notified of amendment. | high (federal regulation, binding) |
| E10 | standards | HL7 FHIR R4 Provenance Resource specification | 2026-02-25 | FHIR Provenance tracks: who (agent), what (target resource), when (recorded/occurred), why (reason), how (activity). Supports revision chains via `entity.role` = derivation/revision/quotation/source. The `_history` API returns all versions of a resource. | high (international healthcare standard) |
| E11 | literature | Cross, R. *Precedent in English Law.* 4th ed., Clarendon Press, 1991 | 2026-02-25 | Stare decisis: lower courts bound by higher courts. Overruling vs distinguishing vs disapproving. Only ratio decidendi (core holding) is binding; obiter dicta (tangential remarks) are persuasive only. When precedent overruled, downstream cases that relied on it are not automatically void — they require individual challenge. | high (standard jurisprudence text) |
| E12 | literature | Adler, B.T. et al. "Wikipedia Vandalism Detection: Combining Natural Language, Metadata, and Reputation Features." *CLEF 2011* | 2026-02-25 | Features for vandalism detection: edit distance, character distribution, anonymity, user reputation, time since last edit, comment content, insertion of vulgarities/ALL-CAPS. ClueBot NG achieves ~95% precision on clear vandalism but ~40% precision on subtle bad-faith edits. ORES (Wikimedia) uses gradient-boosted trees with 70+ features. | high (peer-reviewed, empirical results) |
| E13 | standards | IAS 8 Accounting Policies, Changes in Accounting Estimates and Errors (IFRS); ASC 250 (US GAAP) | 2026-02-25 | Prior-period errors corrected retrospectively (restate prior periods) if material. Immaterial errors corrected in current period. Materiality threshold: would omission or misstatement influence economic decisions of users? Changes in estimates applied prospectively. All corrections require disclosure in notes. | high (binding accounting standard) |
| E14 | documentation | Wikimedia ORES documentation and API reference | 2026-02-25 | ORES models: damaging (is the edit harmful?), goodfaith (was it well-intentioned?), reverted (will it be reverted?). Key insight: damaging and goodfaith are orthogonal — an edit can be good-faith but damaging (incompetent), or bad-faith but non-damaging (sneaky test edit). Two-axis classification outperforms single-axis. | medium (documentation, confirmed by Wikimedia engineering blog posts) |
| E15 | literature | Forbus, K. and de Kleer, J. *Building Problem Solvers.* MIT Press, 1993, Chapters 6-7 | 2026-02-25 | Practical TMS implementation guide. JTMS label propagation: when a justification's support set changes, propagate IN/OUT labels breadth-first. Circular justifications handled by well-founded semantics: a belief circularly justified with no grounded support defaults to OUT. | high (authoritative textbook) |

### Domain 1: Truth Maintenance Systems (TMS)

**Sources**: Doyle 1979 [E01], de Kleer 1986 [E02], Forbus & de Kleer 1993 [E15]

#### Problem Analogy

A TMS maintains a set of beliefs and their logical justifications. When a premise is retracted, derived beliefs that depend on it must be identified and retracted. This is structurally identical to the dangling proposition problem in anchor mutation: when "Anakin is a wizard" is revised to "Anakin is a bard," dependent beliefs like "specializes in School of Evocation" and "signature spell Lightning Bolt" must be identified and invalidated.

#### Solution Mechanism

**JTMS (Justification-based TMS)** — Doyle 1979:

Each belief node has one or more *justification records*. A justification is a pair: `(in-list, out-list)` where `in-list` contains beliefs that must be IN and `out-list` contains beliefs that must be OUT for the justification to hold. A belief is labeled IN if at least one of its justifications is valid (all in-list members IN, all out-list members OUT). Otherwise it is labeled OUT.

When a belief's status changes, a *label propagation algorithm* runs:

1. Mark the changed node.
2. For each node that lists the changed node in any justification, re-evaluate its label.
3. If the label changes, recursively propagate.
4. Continue until a fixed point is reached (no more label changes).

The key property: **retraction is automatic and transitive**. If node A supports node B, and B supports nodes C and D, then retracting A immediately cascades to B, C, and D (unless they have alternative justifications).

**ATMS (Assumption-based TMS)** — de Kleer 1986:

The ATMS takes a fundamentally different approach: instead of maintaining one current label state, it tracks *all possible consistent environments* simultaneously. Each node's label is the set of minimal assumption sets under which it holds. Retraction of an assumption simply prunes all environments containing that assumption — no backtracking needed.

**Circular dependency handling** (Forbus & de Kleer 1993): JTMS uses well-founded semantics. A belief that is only supported through a circular chain with no grounded (non-circular) support defaults to OUT. This prevents self-sustaining belief loops.

#### Classification Taxonomy

TMS does not explicitly classify changes. It distinguishes only between:
- **Assertion**: adding a new belief with a justification (analogous to NEW_FACT)
- **Retraction**: withdrawing support for an existing belief (analogous to REVISION — the old belief goes OUT, a new one can be asserted)
- There is no concept of "contradiction" as a distinct operation — contradictions are handled by the dependency network (if A and not-A both have valid justifications, the system has an inconsistency that must be resolved by retracting one justification)

#### Cascade Handling

**This is where TMS excels.** The justification dependency graph is the cascade mechanism. Properties:

- **Automatic**: No manual enumeration of affected beliefs needed.
- **Precise**: Only beliefs that actually depend on the retracted node are affected.
- **Multi-hop**: Cascades propagate through arbitrarily deep dependency chains.
- **Alternative justification preserves beliefs**: If node B depends on A via one justification but has an independent justification via C, retracting A does not retract B. This is the *resilience* property — well-supported beliefs survive individual retraction events.
- **Depth-limited in practice**: ATMS environments grow exponentially in the worst case. Real implementations use heuristics to limit environment size.

#### Authority Model

None in classic TMS. All beliefs are equal once justified. There is no concept of "this belief is harder to retract than that one." However, the structure naturally provides something analogous: a belief with many independent justifications is *de facto* more resilient to retraction — removing any single support does not retract it.

#### Transferability

| Aspect | Rating | Feature |
|--------|--------|---------|
| Cascade mechanism (justification graph + label propagation) | **High** | F03 |
| Alternative justification resilience (multi-source support prevents cascade) | **High** | F03 |
| Well-founded semantics for circular dependencies | **Medium** | F03 |
| No authority model | Low (gap) | F02 |
| No change classification | Low (gap) | F01 |

**Key insight for dice-anchors**: The JTMS justification record maps directly to a `DERIVED_FROM` or `JUSTIFIED_BY` relationship in the anchor graph. If we record justification edges at extraction time, we get automatic cascade computation for free by running a JTMS-style label propagation on the Neo4j graph. The "alternative justification" property is particularly valuable — an anchor corroborated by multiple independent sources should survive the retraction of any single source.

---

### Domain 2: AGM Belief Revision

**Sources**: Alchourrón, Gärdenfors & Makinson 1985 [E03], Gärdenfors 1988 [E04]

#### Problem Analogy

The AGM framework addresses the exact theoretical question: given a set of beliefs and a new piece of information that contradicts something you believe, how should you rationally update your beliefs? This IS the revision-vs-contradiction problem formalized as a mathematical theory.

#### Solution Mechanism

Three operations on a belief set K:

1. **Expansion (K + p)**: Add belief p to K. No conflict resolution needed. Analogous to adding a new anchor with no existing conflicts.

2. **Contraction (K - p)**: Remove belief p from K while preserving consistency and changing as little as possible. This is the hard operation. Contraction is governed by **epistemic entrenchment**: beliefs are ranked by how firmly held they are, and during contraction, the least-entrenched beliefs are removed first.

3. **Revision (K * p)**: Add belief p to K, resolving conflicts. Defined by the **Levi identity**: K * p = (K - not-p) + p. That is: first contract K to remove the negation of p (making room for p), then expand K to include p.

The **entrenchment ordering** is a total pre-order on beliefs satisfying:
- If A is less entrenched than B, and you must give up one, you give up A.
- Tautologies are maximally entrenched (never retracted).
- If A is not in K, A has minimal entrenchment.
- **Conjunctive condition**: If A and B are both less entrenched than A-and-B, then either A or B must have the same entrenchment as A-and-B (you cannot have both conjuncts less entrenched than their conjunction).

The **minimal change principle** (AGM postulate K*5, also called "inclusion"): the revision K * p should not add any belief that was not in the original K or logically implied by K + p. In other words, revision should not introduce arbitrary new beliefs — it should only add what's necessary.

#### Classification Taxonomy

AGM implicitly classifies changes via the three operations:

| AGM Operation | Anchor Analogue | Condition |
|---------------|-----------------|-----------|
| Expansion | NEW_FACT | New belief, no conflict with existing set |
| Revision | REVISION | New belief conflicts with existing set; resolved by entrenchment |
| Contraction | RETRACTION (no anchor equivalent yet) | Belief removed without replacement |

AGM does not separately model "world progression" vs "revision" — both are handled by the revision operator if they conflict with existing beliefs, or expansion if they don't.

#### Cascade Handling

The **Levi identity** is the cascade mechanism: revision = contraction + expansion. Contraction removes not just the directly contradicted belief but all beliefs that cannot be maintained without it (determined by logical closure and entrenchment ordering). The minimal change principle bounds the cascade — remove only what's necessary.

Specifically, Gärdenfors's *epistemic entrenchment* ordering determines cascade scope:

1. Identify all beliefs logically dependent on the belief being contracted.
2. Among those, retain beliefs that have independent justification (high entrenchment from other sources).
3. Retract only beliefs whose entrenchment derives solely from the contracted belief.

This is remarkably similar to the JTMS "alternative justification" resilience property — beliefs with independent support survive contraction.

#### Authority Model

**Epistemic entrenchment IS an authority model.** It is a ranking of beliefs by firmness, where:
- Higher-entrenched beliefs are retained preferentially during conflict.
- Lower-entrenched beliefs are sacrificed first.
- The ranking reflects both logical centrality and epistemic commitment.

This maps directly to anchor rank/authority:

| AGM Concept | Anchor Equivalent |
|-------------|-------------------|
| Entrenchment ordering | rank (100-900) |
| Maximally entrenched (tautologies) | CANON authority |
| Minimally entrenched (tentative) | PROVISIONAL authority |
| Entrenchment from independent sources | Reinforcement count / corroboration |

#### Transferability

| Aspect | Rating | Feature |
|--------|--------|---------|
| Levi identity (revision = contraction + expansion) | **High** | F01, F02 |
| Entrenchment ordering (rank-based retraction priority) | **High** | F02, F03 |
| Minimal change principle (bounded cascade) | **High** | F03 |
| Three-operation taxonomy (expansion/contraction/revision) | **High** | F01 |
| Formal postulates as validation criteria | **Medium** | F01, F02 |

**Key insight for dice-anchors**: The AGM framework provides a **formal validation criterion** for our revision rules. We can verify that our F01 classification + F02 compliance rules + F03 cascade logic satisfy the AGM postulates. If they do, we have a mathematical guarantee of rational belief revision. If they violate a postulate, we have a specific deficiency to address.

The entrenchment ordering maps almost exactly onto anchor rank. The key adaptation: AGM assumes a static, pre-defined entrenchment ordering, while anchor rank is *dynamic* (changes via reinforcement/decay). This is acceptable — the AGM framework can be applied at any point in time using the current rank snapshot.

---

### Domain 3: MVCC (Multi-Version Concurrency Control)

**Sources**: PostgreSQL documentation [E05], Berenson et al. 1995 [E06]

#### Problem Analogy

MVCC solves: "How do you allow multiple actors to read and write the same data concurrently without corruption?" The relevant sub-problem: how does the system distinguish a legitimate UPDATE from a conflicting concurrent write?

In PostgreSQL, an UPDATE is internally treated as DELETE old version + INSERT new version. The old version persists (with its xmax set to the updating transaction's ID) until vacuumed. This is structurally identical to anchor supersession: the old anchor is marked SUPERSEDED and a new anchor is created.

#### Solution Mechanism

**Version chain**: Each row has a chain of versions, each with:
- `xmin`: Transaction ID that created this version (birth)
- `xmax`: Transaction ID that deleted/updated this version (death, or 0 if current)
- `ctid`: Physical pointer to next version in chain

**Visibility rules** determine which version a transaction sees based on its snapshot (the set of committed transactions at snapshot time). A version is visible if:
1. `xmin` is committed and precedes the snapshot, AND
2. `xmax` is either 0 (not deleted), or the deleting transaction is not yet committed at snapshot time.

**Write-write conflict detection**: Under snapshot isolation, if transaction T1 updates a row and transaction T2 tries to update the same row:
- T2 blocks until T1 commits or aborts.
- If T1 commits: T2 aborts (first-committer-wins).
- If T1 aborts: T2 proceeds.

The critical distinction: MVCC does NOT distinguish between "legitimate update" and "conflicting write" at the mechanism level. Both are write-write conflicts on the same row. The distinction is made at the **application level** — the application decides whether to retry, merge, or abort.

**Foreign key cascade**: PostgreSQL's `ON UPDATE CASCADE` propagates updates to dependent rows. This is a declarative cascade mechanism: the schema defines dependency relationships, and the database engine propagates changes automatically. Analogous to declaring `DERIVED_FROM` edges and auto-cascading supersession.

#### Classification Taxonomy

MVCC distinguishes:
- **Non-conflicting writes**: Different rows, no interaction. (NEW_FACT)
- **Write-write conflict**: Same row, concurrent transactions. (CONTRADICTION)
- **Sequential update**: Same row, non-overlapping transactions. (REVISION — the "legitimate" case)

The key insight: MVCC uses **transaction ordering** (temporal) to distinguish revision from conflict. If your write follows the previous write (sequential), it's an update. If it overlaps (concurrent), it's a conflict. This maps to our problem: a revision is a *subsequent* statement by the same or authorized actor; a contradiction is a *competing* statement from an unauthorized source.

#### Cascade Handling

`ON UPDATE CASCADE` / `ON DELETE CASCADE` in foreign key constraints. Properties:
- **Declarative**: Cascade relationships declared in schema, not in application code.
- **Automatic**: Database engine propagates changes.
- **Bounded by schema**: Only declared foreign key relationships trigger cascade.
- **Transitive**: Cascades chain through multi-level foreign keys.
- **Configurable per relationship**: `CASCADE`, `SET NULL`, `SET DEFAULT`, `RESTRICT`, `NO ACTION`.

The `RESTRICT` vs `CASCADE` choice per foreign key is analogous to per-anchor cascade eligibility (CANON = RESTRICT; PROVISIONAL = CASCADE).

#### Authority Model

MVCC has a simple authority model: **transaction identity**. A transaction can only modify data it has appropriate privileges for (GRANT/REVOKE on tables). There is no per-row authority — authority is per-table or per-schema. Row-level security (RLS) policies in PostgreSQL add per-row authority, but this is outside core MVCC.

#### Transferability

| Aspect | Rating | Feature |
|--------|--------|---------|
| Version chain (xmin/xmax bi-temporal model) | **High** (already adopted) | F04 |
| ON UPDATE CASCADE (declarative cascade) | **High** | F03 |
| Configurable cascade action per FK (CASCADE/RESTRICT/SET NULL) | **High** | F03 |
| Transaction ordering for revision vs conflict | **Medium** | F01 |
| First-committer-wins conflict resolution | **Medium** | F02 |

**Key insight for dice-anchors**: The ON UPDATE CASCADE model with per-relationship action types (CASCADE vs RESTRICT) maps elegantly onto authority-gated cascade. CANON anchors get `RESTRICT` (cannot be cascaded). PROVISIONAL anchors get `CASCADE` (auto-invalidated). RELIABLE anchors get `SET NULL` equivalent (marked for review but not auto-invalidated). This per-edge cascade policy is more flexible than our current per-authority-level policy.

---

### Domain 4: CRDTs (Conflict-free Replicated Data Types)

**Sources**: Shapiro et al. 2011 [E07]

#### Problem Analogy

CRDTs solve: "How do you allow multiple replicas to accept writes independently and still converge to the same state?" The relevant sub-problem: when two actors write different values for the same logical datum, how is the conflict resolved?

This maps to the multi-actor anchor scenario: Player says "wizard," DM generates dependent facts, Player revises to "bard." In a CRDT model, the Player and DM are concurrent replicas that eventually need to converge.

#### Solution Mechanism

Three CRDT patterns are relevant:

**1. Last-Writer-Wins Register (LWW-Register)**:
- Each write carries a timestamp.
- On merge: value with the highest timestamp wins.
- Simple, but discards the "losing" value with no negotiation.
- Maps to: most recent statement wins (temporal revision).

**2. Multi-Value Register (MV-Register)**:
- Concurrent writes are ALL preserved as a set of values.
- On read: application receives the full conflict set and must resolve.
- Maps to: present conflicting anchor values to the user/operator for resolution (F05 UI-controlled mutation).

**3. OR-Set (Observed-Remove Set)**:
- Add-wins semantics: if one replica adds element e while another concurrently removes e, the add wins.
- Each element tagged with a unique ID per add operation. Remove only removes specific tags, not the element universally.
- Maps to: when adding a new anchor conflicts with removing an old one, the add (new fact) takes precedence over the remove (retraction of old fact) — but this needs careful consideration against our "resist adversarial drift" requirement.

**4. Causal ordering (vector clocks)**:
- Not a CRDT per se, but foundational. Vector clocks detect whether two operations are causally ordered (one happened-before the other) or concurrent.
- If causally ordered: later operation supersedes (this is a REVISION).
- If concurrent: conflict — requires resolution strategy (this is a CONTRADICTION).
- Maps directly to our classification problem: revision = causally ordered update; contradiction = concurrent conflicting write.

#### Classification Taxonomy

CRDTs distinguish:
- **Causally ordered updates**: One happened-before the other (REVISION — later supersedes earlier).
- **Concurrent updates**: Neither happened-before the other (CONTRADICTION — requires merge strategy).
- **Compatible operations**: Commutative operations that don't conflict regardless of order (WORLD_PROGRESSION — additive facts).

This three-way distinction maps remarkably well to our F01 taxonomy.

#### Cascade Handling

CRDTs have no built-in cascade mechanism. Each CRDT datum is independent. Composite CRDTs (e.g., a CRDT map containing CRDT registers) handle nested structures, but "if this register changes, that other register becomes invalid" is not modeled. Cascade would need to be an application-level concern.

#### Authority Model

Standard CRDTs have no authority model — all replicas are equal. However, extensions exist:

- **Priority-based LWW**: Instead of timestamp, use (priority, timestamp) pairs. Higher-priority writes win regardless of timestamp. This directly models authority-weighted revision.
- **Operation-based CRDTs with access control**: Each operation annotated with origin identity, and merge functions can weight based on origin authority.

These extensions are research-level, not mainstream implementations. [inference — based on awareness of academic work in this area, not confirmed against a specific paper]

#### Transferability

| Aspect | Rating | Feature |
|--------|--------|---------|
| Causal ordering for revision vs concurrent conflict | **High** | F01 |
| MV-Register (preserve conflicts for human resolution) | **Medium** | F05 |
| Priority-based LWW (authority-weighted resolution) | **Medium** | F02 |
| OR-Set add-wins semantics | **Low** (conflicts with long-horizon consistency controls) | — |
| No built-in cascade | Low (gap) | F03 |

**Key insight for dice-anchors**: The CRDT causal ordering concept provides a clean formal criterion for revision vs contradiction: if the new statement causally follows (i.e., the speaker is aware of the old statement and is intentionally updating it), it is a revision. If the new statement is "concurrent" (the speaker is not referencing the old statement), it is a potential contradiction. Detecting causal ordering in natural language is harder than in distributed systems, but the principle is sound and can inform F01 prompt design: does the user's statement reference or acknowledge the existing anchor?

---

### Domain 5: Engineering Change Orders (ECO)

**Sources**: CMII Standard, ISO 10007:2017 [E08]

#### Problem Analogy

Manufacturing faces the exact same problem: a part specification changes, and all assemblies using that part must be evaluated. Some assemblies can accommodate the change (the part is interchangeable with its replacement); others cannot (the change breaks fit, form, or function at the assembly level). The ECO process is a formalized, battle-tested cascade evaluation framework.

#### Solution Mechanism

The ECO process has distinct stages:

**1. Change Request (CR)**: An authorized actor proposes a change. The CR includes: what is changing, why, who is requesting it, and a preliminary impact assessment. This is the equivalent of a revision intent signal.

**2. Change Classification**:
- **Class I (Major)**: Affects form, fit, or function. Requires full impact assessment, customer notification, re-qualification. Cannot be applied without approval.
- **Class II (Minor)**: Documentation-only change. No interchangeability impact. Can be applied with minimal approval.

This two-class system maps to authority-gated revision: PROVISIONAL/UNRELIABLE anchors = Class II (minor, fast-track); RELIABLE anchors = Class I (major, requires assessment); CANON = no change without formal review board.

**3. Impact Assessment Matrix**: A cross-reference table where:
- Rows = changed items (parts, specifications)
- Columns = all parent assemblies (BOMs that reference the changed item)
- Cells = interchangeability verdict (interchangeable / non-interchangeable / requires analysis)

This is a declarative cascade evaluation. The "where-used" query (which assemblies use this part?) is the BOM equivalent of "which anchors depend on this anchor?"

**4. Interchangeability Analysis**: For each affected assembly, evaluate whether the new version of the part can replace the old version without impacting the assembly's form, fit, or function. Three outcomes:
- **Interchangeable**: Assembly unaffected. No cascade. (Anchor equivalent: dependent anchor remains valid after revision.)
- **Non-interchangeable, can be adapted**: Assembly needs modification. Cascade with revision. (Anchor equivalent: dependent anchor needs re-evaluation.)
- **Non-interchangeable, cannot be adapted**: Assembly obsoleted. Full cascade. (Anchor equivalent: dependent anchor invalidated.)

**5. Effectivity dates**: Changes have a defined effectivity — they take effect at a specific point (serial number, date, lot). This is the bi-temporal model: the change is recorded now but takes effect in the future.

#### Classification Taxonomy

| ECO Class | Interchangeability | Anchor Analogue |
|-----------|--------------------|-----------------|
| Class I, non-interchangeable | Full cascade | REVISION of RELIABLE anchor |
| Class I, interchangeable | No cascade | WORLD_PROGRESSION (additive change) |
| Class II | Documentation cascade only | REVISION of PROVISIONAL anchor |
| No change (request rejected) | — | CONTRADICTION (blocked) |

#### Cascade Handling

**This is the ECO's core competency.** The BOM dependency tree + impact assessment matrix is the most mature cascade framework surveyed:

- **Where-used explosion**: From the changed part, traverse the BOM upward through all parent assemblies, recursively.
- **Interchangeability filter**: At each level, evaluate whether the change propagates or is absorbed.
- **Configurable depth**: Analysis can be bounded to N levels of BOM hierarchy.
- **Parallel impact assessment**: Multiple engineers evaluate affected assemblies concurrently.
- **Effectivity control**: Cascade effects can be staged (apply to new production immediately, retrofit existing units on a schedule).

#### Authority Model

ECO authority is role-based with escalation:
- **Engineer**: Can initiate Class II changes.
- **Change Review Board (CRB)**: Must approve Class I changes.
- **Customer**: Must approve changes affecting form/fit/function on their products.
- **Quality**: Must approve changes affecting safety-critical items.

This maps to: PROVISIONAL revision = engineer-level (auto-approve); RELIABLE revision = CRB (operator confirmation); CANON revision = customer/quality approval (CanonizationGate).

#### Transferability

| Aspect | Rating | Feature |
|--------|--------|---------|
| Change classification (Class I/II) | **High** | F01, F02 |
| Impact assessment matrix (where-used + interchangeability) | **High** | F03 |
| Interchangeability analysis (does cascade propagate or absorb?) | **High** | F03 |
| Effectivity dates (staged cascade) | **Medium** | F04 |
| Role-based authority escalation | **High** | F02 |
| Change request as explicit intent signal | **Medium** | F01 |

**Key insight for dice-anchors**: The interchangeability analysis concept is directly transferable to F03 cascade. When "wizard" is revised to "bard," each dependent anchor should be evaluated for "interchangeability" — does the dependent fact remain valid with the new parent value? "Anakin is human" is interchangeable (class doesn't affect race). "Specializes in School of Evocation" is non-interchangeable (class-specific). This per-dependency evaluation is more precise than blanket cascade and prevents over-invalidation.

---

### Domain 6: Medical Records

**Sources**: HIPAA 45 CFR 164.526 [E09], HL7 FHIR Provenance [E10]

#### Problem Analogy

Medical records must be simultaneously immutable (for legal/audit purposes) and correctable (because errors in medical records can kill people). The tension between preservation and correction is exactly our tension between consistency control and revision acceptance.

#### Solution Mechanism

HIPAA 45 CFR 164.526 defines a precise amendment process:

**1. Original entry preservation**: The original entry is NEVER deleted or overwritten. It is marked with a link to the amendment. Analogous to supersession with `SUPERSEDES` relationship.

**2. Amendment vs Addendum vs Correction**:
- **Amendment**: Change to the meaning of an existing entry. The original author or an authorized provider asserts that the original entry was incorrect or incomplete. Requires formal process.
- **Addendum**: New information that supplements an existing entry. Does not change the meaning of the original. Analogous to WORLD_PROGRESSION.
- **Correction**: Fixes a clerical error (typo, wrong date field, etc.). Less formal than amendment.
- **Late entry**: Documentation of a clinical event that was not recorded at the time it occurred. Not a correction of existing data but filling a gap.

**3. Amendment request process**:
- Patient (or authorized representative) submits written request.
- Provider has 60 days to accept or deny.
- Provider MAY deny if: (a) the original information is accurate and complete; (b) the information was not created by the provider; (c) the information is not part of the designated record set; (d) the information would not be available for inspection.
- If denied, the patient can submit a statement of disagreement, which is permanently appended to the record.
- If accepted, the amendment is appended (not substituted) and all entities who received the original information are notified.

**4. Cascade notification**: When an amendment is accepted, the provider must "make reasonable efforts to inform and provide the amendment to persons identified by the individual as having received the protected health information and needing the amendment" and to "persons, including business associates, that the covered entity knows have the protected health information and that may have relied on such information to the individual's detriment."

This is a cascade mechanism — not automatic invalidation, but mandatory notification of downstream consumers.

#### Classification Taxonomy

| Medical Record Change | Anchor Analogue | Authority Required |
|----------------------|-----------------|-------------------|
| Amendment | REVISION | Authorized provider, formal request |
| Addendum | WORLD_PROGRESSION | Original or treating provider |
| Correction (clerical) | MINOR_REVISION (not currently in F01) | Any authorized user |
| Late entry | NEW_FACT (retroactive) | Treating provider |
| Denial of amendment | CONTRADICTION (blocked) | Provider judgment |

**Key finding**: The medical record taxonomy has FOUR categories of change, not three. The "correction" category (clerical fixes that don't change clinical meaning) suggests a potential gap in our REVISION/CONTRADICTION/WORLD_PROGRESSION taxonomy — minor textual corrections that don't warrant full revision processing.

#### Cascade Handling

Medical record cascade is notification-based, not automatic invalidation:

1. Identify all downstream consumers of the original information.
2. Notify each consumer of the amendment.
3. Consumers independently evaluate whether the amendment affects their downstream decisions.

This is a "soft cascade" — the system notifies but does not automatically invalidate dependent records. The rationale: a prescription based on a now-amended diagnosis may still be appropriate; only a clinical judgment can determine whether the downstream decision needs to change.

This maps to a cascade strategy for RELIABLE anchors: instead of auto-invalidating, flag dependent anchors for review and let the operator (or an LLM evaluation pass) determine which dependents are actually affected.

#### Authority Model

Strict role-based:
- **Original author**: Can amend their own entries.
- **Treating provider**: Can add addenda.
- **Patient**: Can REQUEST amendment; cannot directly amend.
- **Unauthorized actors**: No modification permitted.

The "original author can amend" principle maps directly to F01's revision eligibility: the actor who introduced a fact has standing to revise it.

#### Transferability

| Aspect | Rating | Feature |
|--------|--------|---------|
| Amendment/addendum/correction taxonomy | **High** | F01 |
| Original entry preservation (append-only with link) | **High** (already adopted) | F04 |
| Cascade notification (soft cascade) | **High** | F03 |
| Original-author amendment privilege | **High** | F01, F02 |
| Denial with statement of disagreement | **Medium** | F02 |
| Four-category change taxonomy | **Medium** | F01 |

**Key insight for dice-anchors**: The medical "soft cascade" (notify + evaluate) is more appropriate for RELIABLE anchors than hard cascade (auto-invalidate). It avoids over-invalidation while ensuring dependents are reviewed. Also, the four-category taxonomy (amendment/addendum/correction/late entry) suggests we may need a MINOR_CORRECTION category for trivial fixes (typos in anchor text) that should bypass full revision processing.

---

### Domain 7: Legal Precedent / Case Law

**Sources**: Cross 1991 [E11], general jurisprudence knowledge

#### Problem Analogy

Legal precedent is an authority hierarchy where established rulings resist change (stare decisis) but can be revised under specific conditions. The parallel to anchor authority is striking:

| Legal Concept | Anchor Concept |
|---------------|----------------|
| Trial court ruling | PROVISIONAL |
| Appellate ruling | UNRELIABLE/RELIABLE |
| Supreme court ruling | CANON |
| Stare decisis | Drift resistance |
| Overruling | REVISION (authorized supersession) |
| Distinguishing | NOT_MENTIONED (different context) |
| Following | REINFORCEMENT |

#### Solution Mechanism

Legal systems handle revision of established authority through several mechanisms:

**1. Overruling**: A higher court explicitly declares that a previous ruling was wrong and replaces it with a new rule. Requirements:
- The overruling court must have **hierarchical authority** over the court that issued the original ruling (or be the same court).
- The overruling must be **explicit** — the court must acknowledge it is changing the law, not merely applying it differently.
- The original ruling remains in the record (not deleted) but is marked as "overruled."

**2. Distinguishing**: A court facing a seemingly applicable precedent identifies a factual or legal difference that makes the precedent inapplicable to the present case. The precedent remains valid; it simply doesn't apply here. This is NOT revision — the original authority is unchanged. Analogous to NOT_MENTIONED in drift evaluation.

**3. Disapproving / Doubting**: A court expresses disagreement with a precedent without formally overruling it (usually because it lacks the hierarchical authority to overrule). The precedent remains binding but its authority is weakened. Analogous to rank decay without authority downgrade.

**4. Ratio decidendi vs Obiter dicta**: Only the core legal holding (ratio) is binding precedent. Tangential observations (obiter dicta) are persuasive but not binding. This creates two tiers of "stickiness" within a single ruling — core holdings resist change, tangential remarks are more easily departed from.

#### Classification Taxonomy

| Legal Action | Anchor Analogue | Authority Requirement |
|-------------|-----------------|----------------------|
| Following precedent | REINFORCEMENT | Any court |
| Distinguishing | NOT_MENTIONED | Any court |
| Overruling | REVISION | Equal or higher court |
| Disapproving | RANK_DECAY (no current equivalent) | Any court |
| Prospective overruling | STAGED_REVISION (no current equivalent) | Higher court |

**Prospective overruling** is notable: some jurisdictions allow a court to overrule a precedent but only for future cases, not the present one. The old rule applies to the current case; the new rule applies going forward. This is an effectivity-date concept — the revision takes effect in the future, not retroactively.

#### Cascade Handling

When a precedent is overruled, there is NO automatic cascade. Downstream cases that relied on the overruled precedent are not automatically vacated. Instead:

1. They remain in effect until individually challenged.
2. Parties must bring new proceedings to argue that the overruling affects their case.
3. Courts evaluate each downstream case individually — some may be unaffected (the overruled point wasn't the basis of the downstream decision).

This is another instance of "soft cascade" — the system propagates authority change but requires individual evaluation of each dependent.

Legal scholars have noted this as a weakness: the lack of automatic cascade means overruled precedent can continue to influence decisions for years or decades until downstream cases are individually revisited. [confirmed — this is well-documented in legal scholarship on precedent]

#### Authority Model

Strict hierarchy:
- Trial courts < Appellate courts < Supreme court.
- A court can only overrule precedent from its own level or below.
- Horizontal stare decisis (court bound by its own prior decisions) is strong but not absolute — courts can overrule their own precedent under "compelling reasons."

The "compelling reasons" threshold maps to authority-level gating: overruling RELIABLE-equivalent authority requires stronger justification than overruling PROVISIONAL-equivalent authority.

#### Transferability

| Aspect | Rating | Feature |
|--------|--------|---------|
| Hierarchical authority for overruling | **High** | F02 |
| Distinguishing vs overruling distinction | **Medium** | F01 |
| Ratio/obiter dicta (core vs peripheral elements) | **Medium** | F03 |
| Soft cascade (individual challenge required) | **Medium** | F03 |
| Prospective overruling (effectivity dates) | **Low** | F04 |

**Key insight for dice-anchors**: The ratio/obiter dicta distinction suggests a useful heuristic for cascade scope. When evaluating whether a dependent anchor is affected by a parent revision, ask: is the dependency through the "core holding" (essential relationship — "wizard" → "School of Evocation") or "tangential" (incidental co-occurrence — "wizard" → "human")? Only core dependencies should cascade.

---

### Domain 8: Wikipedia Edit Classification

**Sources**: Adler et al. 2011 [E12], Wikimedia ORES documentation [E14]

#### Problem Analogy

Wikipedia is the largest-scale implementation of collaborative knowledge revision. Its automated edit quality systems (ClueBot NG, ORES) solve exactly the F01 problem: is this edit constructive or destructive? Wikipedia's problem is harder than ours in some ways (open editing by anonymous users) and easier in others (human reviewers as a backstop).

#### Solution Mechanism

**ClueBot NG** (rule-based + ML):
- Uses a multi-layer approach: first, rule-based filters catch obvious vandalism (blanking, mass deletions, known slurs). Then, a neural network classifier evaluates remaining edits.
- Features include: edit size, character distribution, user account age, user edit count, time of day, whether the edit adds or removes content, presence of specific patterns (ALL CAPS, repeated characters, vulgar terms).
- Achieves ~95% precision and ~58% recall on clear vandalism.
- Key limitation: ~40% precision on subtle bad-faith edits (sophisticated manipulation that looks legitimate).

**ORES (Objective Revision Evaluation Service)**:
- Two orthogonal models:
  - **damaging**: Is the edit harmful to article quality? (factual accuracy, readability, neutrality)
  - **goodfaith**: Was the edit made with good intentions? (even if the result is damaging)
- This two-axis classification is a crucial insight: an edit can be good-faith but damaging (well-intentioned but wrong) or bad-faith but non-damaging (test edit, sneaky probe). These require different responses.
- Uses gradient-boosted trees with 70+ features spanning: content diff features, user features (registration age, edit count, groups), temporal features, page features (watchers, protection level).
- Precision/recall varies by wiki and threshold, but typical configurations achieve ~90% precision at ~70% recall for "damaging" predictions.

**Key features used by ORES** (relevant to F01 classification):

| Feature Category | Example Features | Anchor Analogue |
|-----------------|-----------------|-----------------|
| User reputation | Edit count, account age, user groups | Actor authority / trust score |
| Edit characteristics | Characters added/removed, largest continuous addition | Magnitude of anchor text change |
| Content features | Presence of citations, links, templates | Extraction confidence, corroboration count |
| Temporal features | Time since page last edited, time of day | Turn number, time since anchor creation |
| Page features | Number of watchers, protection level | Anchor authority level, reinforcement count |
| Revertion features | Number of recent reverts to this page | Recent conflict count on this anchor |

#### Classification Taxonomy

ORES uses a two-axis taxonomy:

|                  | Good-faith | Bad-faith |
|-----------------|------------|-----------|
| **Non-damaging** | Normal edit | Sneaky test edit |
| **Damaging**     | Incompetent edit | Vandalism |

This maps to our problem:

| ORES Category | Anchor Analogue | Handling |
|---------------|-----------------|----------|
| Good-faith, non-damaging | REVISION or WORLD_PROGRESSION | Accept |
| Good-faith, damaging | REVISION with errors (needs correction) | Accept with review |
| Bad-faith, non-damaging | Probing attack (no actual contradiction) | Monitor |
| Bad-faith, damaging | CONTRADICTION (adversarial drift) | Reject |

The two-axis insight is important: our F01 taxonomy conflates intent and outcome into a single classification. A user could genuinely want to revise their character (good-faith) but propose something inconsistent with world rules (damaging). Treating this as a flat CONTRADICTION is wrong.

#### Cascade Handling

Wikipedia has minimal automated cascade handling. When an edit is reverted:
- Only the specific edit is reverted.
- Downstream edits by other users that built on the reverted edit are NOT automatically reverted.
- Other editors are expected to notice and fix inconsistencies.
- Bot-assisted cleanup exists for specific patterns (e.g., if a category is deleted, bots remove category tags from member articles).

This is the weakest cascade model in the survey — essentially no cascade.

#### Authority Model

Wikipedia uses a multi-tier privilege model:
- **Anonymous users**: Can edit, but edits reviewed more heavily.
- **Autoconfirmed users**: Account age > 4 days, > 10 edits. Can edit semi-protected pages.
- **Extended confirmed**: > 30 days, > 500 edits. Can edit extended-confirmed-protected pages.
- **Administrators**: Full access, can protect/unprotect pages.
- **Page protection levels**: None / Semi / Extended / Full — restrict who can edit.

The per-page protection level is analogous to per-anchor authority gating: higher-authority anchors require higher-privilege actors to modify.

#### Transferability

| Aspect | Rating | Feature |
|--------|--------|---------|
| Two-axis classification (damaging x goodfaith) | **High** | F01 |
| Feature engineering for edit quality prediction | **High** | F01 |
| User reputation as classification signal | **Medium** | F01 |
| Per-page protection levels (authority gating) | **Medium** | F02 |
| Weak cascade model | Low (gap) | F03 |

**Key insight for dice-anchors**: The ORES two-axis model (damaging x good-faith) is directly applicable to F01. Our current binary (REVISION vs CONTRADICTION) collapses two independent dimensions. A richer classification would be:

| Intent | Impact | Classification | Action |
|--------|--------|---------------|--------|
| Revision-intent | Consistent | REVISION | Accept, supersede |
| Revision-intent | Inconsistent | REVISION_WITH_ISSUES | Accept with cascade review |
| No revision-intent | Consistent | WORLD_PROGRESSION | Accept, no supersession |
| No revision-intent | Inconsistent | CONTRADICTION | Reject |

This 2x2 model is more nuanced than our 3-way taxonomy and may improve classification accuracy by decomposing the problem.

---

### Domain 9: Accounting — Double-Entry Bookkeeping

**Sources**: IAS 8 / ASC 250 [E13]

#### Problem Analogy

Accounting entries are immutable once posted — the double-entry system requires a balanced audit trail, and altering a posted entry would break the trail. Yet errors must be correctable. The solution: never modify, only supersede via explicit reversing and correcting entries. This is the purest real-world implementation of the supersession pattern.

#### Solution Mechanism

**Reversing entry + correcting entry**:

1. **Original entry** (incorrect): Debit Cash $500, Credit Revenue $500 (posted Jan 1)
2. **Reversing entry**: Debit Revenue $500, Credit Cash $500 (posted Feb 15, explicitly references original)
3. **Correcting entry**: Debit Cash $300, Credit Revenue $300 (posted Feb 15, the correct amount)

The net effect: Revenue shows $300, not $500. The audit trail shows all three entries — the original, the reversal, and the correction. This is structurally identical to anchor supersession with provenance.

**Materiality threshold**: Not all errors require the full reversal process:

| Error Severity | Treatment | Analogous Authority Level |
|----------------|-----------|--------------------------|
| **Material prior-period error** | Retrospective restatement: restate prior-period comparatives as if the error never occurred. Full disclosure in notes. | RELIABLE anchor revision (requires formal process, notification) |
| **Immaterial prior-period error** | Correct in the current period. No restatement of priors. Minimal disclosure. | PROVISIONAL anchor revision (lightweight process) |
| **Change in accounting estimate** | Prospective treatment only: adjust current and future periods. Prior periods not restated. | WORLD_PROGRESSION (new information, not correction of error) |
| **Change in accounting policy** | Retrospective application unless impracticable. | CANON-level change (systemic, affects everything) |

The materiality concept provides a principled threshold for how much process is required for a correction. This maps to authority-level gating: higher-authority anchors require more formal revision processes.

**IAS 8 defines materiality as**: "Omissions or misstatements of items are material if they could, individually or collectively, influence the economic decisions that users make on the basis of the financial statements." In anchor terms: a revision is "material" if it could influence downstream decisions (dependent anchors, narrative consistency).

#### Classification Taxonomy

| Accounting Change | Anchor Analogue | Direction |
|-------------------|-----------------|-----------|
| Error correction (material) | REVISION (high-authority) | Retrospective |
| Error correction (immaterial) | REVISION (low-authority) | Current-period only |
| Change in estimate | WORLD_PROGRESSION | Prospective |
| Change in policy | SYSTEMIC_REVISION (no current equivalent) | Retrospective |

#### Cascade Handling

Retrospective restatement is the accounting cascade:
- All prior-period financial statements are restated as if the error never occurred.
- Comparative figures in all subsequent reports are adjusted.
- Retained earnings are adjusted for the cumulative effect.

This is a **hard cascade** — automatic, retrospective, comprehensive. But it is bounded by materiality: immaterial errors do not trigger cascade.

Prospective changes (estimate changes) have NO cascade to prior periods — only current and future periods are affected. This is the "effectivity date" concept again.

#### Authority Model

- **Controller/CFO**: Approves material corrections.
- **External auditors**: Must approve material prior-period restatements.
- **Staff accountants**: Can make immaterial current-period corrections.
- **Board of Directors**: Approves changes in accounting policy.

This maps cleanly to:
| Accounting Role | Anchor Role | Can Revise |
|----------------|-------------|------------|
| Staff accountant | Regular actor | PROVISIONAL |
| Controller | Operator | RELIABLE (with justification) |
| Board + auditors | CanonizationGate | CANON |

#### Transferability

| Aspect | Rating | Feature |
|--------|--------|---------|
| Reversing + correcting entry pattern | **High** (already adopted as supersession) | F04 |
| Materiality threshold (process proportional to impact) | **High** | F02 |
| Retrospective vs prospective treatment | **Medium** | F03, F04 |
| Error vs estimate vs policy change taxonomy | **Medium** | F01 |
| Authority escalation by materiality | **High** | F02 |

**Key insight for dice-anchors**: The materiality concept provides a principled answer to "how much process should a revision require?" Currently our authority levels (PROVISIONAL/UNRELIABLE/RELIABLE/CANON) serve as crude materiality tiers, but the accounting model suggests a more nuanced approach: materiality should consider not just the anchor's authority but also its **impact radius** (how many dependent anchors exist, how central it is to the narrative). A PROVISIONAL anchor with 10 dependents is more "material" than a RELIABLE anchor with no dependents.

---

## Analysis

### Synthesis Question 1: Which domains have the most direct structural analogy?

**Tier 1 — Near-perfect structural match:**
- **TMS** (JTMS/ATMS): The dependency graph + label propagation mechanism is an almost exact structural match for F03 (cascade). The justification record is our `DERIVED_FROM` edge.
- **AGM Belief Revision**: The formal theory of what we are trying to implement. Entrenchment = rank. Levi identity = supersession. Minimal change = bounded cascade.
- **Medical Records (HIPAA)**: The amendment/addendum/correction taxonomy is a battle-tested version of our REVISION/WORLD_PROGRESSION classification with an additional "minor correction" category. The original-author-can-amend principle directly informs F01/F02.

**Tier 2 — Strong partial match:**
- **Engineering Change Orders**: Best-in-class cascade framework (impact assessment matrix + interchangeability analysis). Weaker on classification but strongest on cascade.
- **Accounting**: Purest supersession model (reversing entries). Materiality threshold provides principled authority-gating logic.
- **Wikipedia/ORES**: Best-in-class edit classification. Two-axis model (damaging x good-faith) is more nuanced than our three-way taxonomy.

**Tier 3 — Useful but partial:**
- **MVCC**: Version chain model already adopted. ON UPDATE CASCADE with per-FK action types adds new insight.
- **CRDTs**: Causal ordering concept useful for revision detection. Priority-based LWW informs authority-weighted resolution.
- **Legal Precedent**: Authority hierarchy analogy is illustrative but less mechanistically transferable.

### Synthesis Question 2: Are there recurring patterns across multiple domains?

**YES — five patterns recur across 3+ domains. These are likely fundamental:**

#### Pattern A: Append-Only with Supersession Link
Domains: Medical records, Accounting, MVCC, Legal
- Never delete or overwrite the original.
- Create a new version linked to the original via an explicit supersession relationship.
- Both versions preserved for audit/history.

**Status in dice-anchors**: Already implemented via the `SUPERSEDES` relationship. Confirmed as a universal pattern.

#### Pattern B: Authority-Gated Revision Eligibility
Domains: Medical records, Accounting, Legal, ECO, Wikipedia
- Higher-authority items require higher-privilege actors and more formal process to revise.
- Lower-authority items can be revised by a broader set of actors with less ceremony.
- The highest-authority items are effectively immutable except through extraordinary process.

**Status in dice-anchors**: Partially implemented (CANON requires CanonizationGate). F02 should formalize this as a continuous authority-gating spectrum, not just a CANON/non-CANON binary.

#### Pattern C: Cascade with Interchangeability/Impact Evaluation
Domains: TMS, AGM, ECO, MVCC
- When a parent item changes, evaluate each dependent item individually.
- Some dependents are unaffected (interchangeable, alternative justification).
- Some dependents are invalidated (non-interchangeable, sole justification).
- Cascade propagates transitively but is bounded by evaluation at each hop.

**Status in dice-anchors**: Not yet implemented. F03 must implement this. The ECO interchangeability analysis and JTMS alternative-justification concepts together provide the design pattern.

#### Pattern D: Change Type Taxonomy
Domains: Medical records, Accounting, AGM, Wikipedia
- All domains distinguish at minimum between: correction of error, new information, and systematic change.
- Medical records have the richest taxonomy (amendment/addendum/correction/late entry).
- Wikipedia adds a second axis (intent vs impact) creating a 2x2 matrix.

**Status in dice-anchors**: Not yet implemented. F01 must implement this. The medical taxonomy + ORES 2-axis model together suggest a richer classification than our current three-way proposal.

#### Pattern E: Original-Author Privilege
Domains: Medical records, Wikipedia, Legal
- The original author has special standing to revise their own contributions.
- Other actors can challenge but typically face a higher bar.
- In medical records, only the original author (or treating provider) can amend.
- In Wikipedia, user reputation (partially based on contribution history) influences how edits are evaluated.

**Status in dice-anchors**: Not yet implemented. F01/F04 together must implement this — F04 records who introduced the anchor, F01 uses that provenance to weight revision classification.

### Synthesis Question 3: Which patterns are cheapest to adapt given existing infrastructure?

1. **Pattern A (Append-Only + Supersession)**: Already done. No cost.
2. **Pattern B (Authority-Gated Revision)**: Low cost — anchor authority levels already exist. F02 adds prompt template carveouts, which is template editing, not engine changes.
3. **Pattern E (Original-Author Privilege)**: Medium cost — requires F04 provenance metadata (new fields on PropositionNode) plus F01 classification prompt changes.
4. **Pattern D (Change Type Taxonomy)**: Medium cost — requires F01 classification, which is an LLM prompt engineering task + new enum + conflict record changes.
5. **Pattern C (Cascade with Evaluation)**: Highest cost — requires F03 dependency graph traversal, interchangeability evaluation (potentially LLM-assisted), and cascade propagation logic. But TMS label propagation provides a clear algorithmic blueprint.

### Synthesis Question 4: Do any domains reveal failure modes or edge cases we haven't considered?

**Yes — four previously unconsidered edge cases:**

1. **Circular dependencies** (TMS): If anchor A supports B, and B supports A (mutual reinforcement), retracting either leaves the other with only circular support. JTMS well-founded semantics say both should go OUT. Our cascade logic needs to handle cycles.

2. **Good-faith-but-damaging edits** (Wikipedia/ORES): A user may sincerely revise their character but propose something inconsistent with world rules. Our binary REVISION/CONTRADICTION doesn't handle this — we'd either accept the inconsistency or reject a good-faith revision. The 2x2 model addresses this.

3. **Prospective vs retrospective revision** (Accounting, Legal): Should a revision take effect immediately, or should it be "prospective" (applying only to future turns, not retroactively invalidating narrative that already happened)? Accounting distinguishes these. We haven't considered this dimension.

4. **Notification cascade vs invalidation cascade** (Medical records): For RELIABLE anchors, perhaps cascade should not auto-invalidate but instead flag dependents for review. Medical records use this "soft cascade" pattern. Our current F03 design assumes hard cascade with authority-gated exemptions, but soft cascade may be more appropriate for middle-authority anchors.

## Recommendation

### Deliverable: Pattern Catalog

The following table organizes all transferable patterns by target feature, with implementation hints specific to the dice-anchors codebase.

#### Patterns for F01 — Revision Intent Classification

| ID | Pattern | Source Domain(s) | Mechanism | Implementation Hint | Confidence |
|----|---------|-----------------|-----------|---------------------|------------|
| P01 | Two-axis classification (intent x impact) | Wikipedia/ORES | Classify conflict along two independent axes: (1) revision intent (does the speaker intend to update?) and (2) consistency impact (is the update consistent with remaining anchors?). This produces a 2x2 matrix, not a flat 3-way enum. | Extend `ConflictType` to include `REVISION`, `REVISION_WITH_ISSUES`, `WORLD_PROGRESSION`, `CONTRADICTION`. The LLM conflict classification prompt should evaluate intent and impact separately, then combine. | high — supported by ORES empirical results |
| P02 | Causal ordering as revision signal | CRDTs | If the new statement causally references or acknowledges the existing anchor, it is a revision. If it introduces a conflicting value without reference, it is a potential contradiction. | Add to F01 classification prompt: "Does the user's message reference, acknowledge, or build upon the existing fact? If yes, classify as REVISION." This is the natural-language equivalent of CRDT vector clock comparison. | medium — sound principle, but NLP detection of causal reference may have false negatives |
| P03 | Original-author privilege as classification signal | Medical records, Wikipedia | The original author of a fact has higher revision credibility than other actors. A player revising their own character vs an adversary injecting contradictions. | Requires F04 provenance metadata. If `speakerRole` of the anchor matches the current actor, weight toward REVISION. Add `speakerRole` match as a signal in the classification prompt or as a pre-filter before LLM classification. | high — universal pattern across domains |
| P04 | Four-category change taxonomy | Medical records | Add MINOR_CORRECTION as a fourth category for trivial fixes (typos, minor clarifications) that bypass full revision processing. | Add `MINOR_CORRECTION` to `ConflictType`. Detect via heuristic: small edit distance between old and new anchor text + no semantic change. Fast-path: supersede without cascade. | medium — useful but may be premature for initial F01 scope |
| P05 | Reputation-weighted classification | Wikipedia/ORES | User reputation (derived from history of interactions) influences classification confidence. A well-established collaborator's edits are more likely to be legitimate revisions. | Long-term: track per-actor interaction quality. Short-term: use actor role (PLAYER vs unknown) as a coarse reputation signal. | low — requires actor tracking infrastructure not yet in scope |

#### Patterns for F02 — Prompt Compliance / Revision Carveout

| ID | Pattern | Source Domain(s) | Mechanism | Implementation Hint | Confidence |
|----|---------|-----------------|-----------|---------------------|------------|
| P06 | Materiality-proportional process | Accounting (IAS 8) | The formality of the revision process should be proportional to the "materiality" of the anchor — a function of both authority AND impact radius (number of dependents, narrative centrality). | Compute `materiality = f(authority, dependentCount, reinforcementCount)`. PROVISIONAL with 0 dependents = immaterial (auto-approve). PROVISIONAL with 10 dependents = material (requires cascade evaluation). Use materiality to determine prompt compliance strictness. | high — principled, addresses the "PROVISIONAL anchor with many dependents" edge case |
| P07 | Authority-escalation gating | ECO (Class I/II), Medical records, Accounting | Different authority levels require different approval processes, not just different prompt language. PROVISIONAL = auto-approve, UNRELIABLE = accept-with-logging, RELIABLE = operator-confirm, CANON = CanonizationGate. | Encode as a `RevisionEligibility` enum or strategy pattern keyed by `Authority`. Wire into `RevisionAwareConflictResolver`. Each level defines: auto-approve threshold, requires-confirmation flag, notification requirements. | high — universal across domains |
| P08 | Denial with disagreement record | Medical records (HIPAA) | When a revision is denied (e.g., CANON anchor, insufficient authority), preserve the revision attempt as metadata. The requester can attach a "statement of disagreement." | Add a `RevisionDenial` event to lifecycle events. Include reason, denied revision text, and optional disagreement note. Persisted for audit. Low priority but supports observability. | medium — good for audit, not critical for initial implementation |

#### Patterns for F03 — Dependent Anchor Cascade

| ID | Pattern | Source Domain(s) | Mechanism | Implementation Hint | Confidence |
|----|---------|-----------------|-----------|---------------------|------------|
| P09 | Justification-based label propagation | TMS (JTMS) | Record justification edges between anchors. When a parent is superseded, propagate OUT labels to dependents. Dependents with alternative justifications (other supporting anchors) remain IN. | Add `JUSTIFIED_BY` relationship type in Neo4j. At cascade time, run BFS from revised anchor. For each dependent, check if it has any remaining IN justification. If not, mark for invalidation. Cypher query: `MATCH (a)-[:JUSTIFIED_BY]->(revised) WHERE NOT EXISTS { MATCH (a)-[:JUSTIFIED_BY]->(other) WHERE other <> revised AND other.status = 'ACTIVE' }`. | high — well-understood algorithm with 40+ years of implementation history |
| P10 | Interchangeability evaluation per dependency | ECO | For each dependency edge, evaluate whether the revision breaks the dependency (non-interchangeable) or is compatible (interchangeable). Only cascade through broken dependencies. | At cascade time, for each dependent anchor, ask: "Is [dependent anchor text] still valid given that [old parent text] has been revised to [new parent text]?" This can be an LLM evaluation call per dependent, or a heuristic based on semantic similarity. | high — reduces over-invalidation compared to blanket cascade |
| P11 | Soft cascade (notify + review) for middle authority | Medical records | For RELIABLE dependents, don't auto-invalidate. Instead, flag them for operator review. Only PROVISIONAL/UNRELIABLE dependents are auto-cascaded. | Cascade action per authority level: `PROVISIONAL` → auto-invalidate; `UNRELIABLE` → auto-invalidate with log; `RELIABLE` → flag for review (emit event, UI indicator); `CANON` → exempt. Mirrors MVCC's per-FK cascade actions (CASCADE / SET NULL / RESTRICT). | high — balances automation with safety for high-value anchors |
| P12 | Circular dependency handling | TMS (JTMS) | Mutual-support cycles (A justifies B, B justifies A) with no grounded support should both be invalidated. Use well-founded semantics: a belief is IN only if it has a justification chain terminating in a non-circular ground truth. | During cascade BFS, detect cycles (already visited nodes). For each cycle, check if any node in the cycle has a justification path to a non-cyclic ground truth. If not, invalidate all cycle members. | medium — edge case, but can cause inconsistency if unhandled |
| P13 | Cascade depth limiting | ECO, TMS | Bound cascade propagation to N hops (configurable, default 1-2). Beyond that depth, flag for review rather than auto-invalidate. Prevents runaway cascade in deep dependency chains. | `SimulationRuntimeConfig` or `DiceAnchorsProperties` gets `cascadeMaxDepth` (default: 2). BFS stops at max depth. Nodes beyond depth flagged for review (P11 soft cascade). | high — practical necessity; unbounded cascade is dangerous |
| P14 | Minimal change principle | AGM | During cascade, remove ONLY what is necessary to restore consistency. If a dependent anchor can remain valid by reinterpreting its relationship to the revised parent, prefer reinterpretation over invalidation. | Before invalidating a dependent, check: (a) does it have alternative justifications? (P09), (b) is it interchangeable with the revised parent? (P10). Only invalidate if both checks fail. This is the AGM "minimal change" principle operationalized. | high — prevents over-invalidation, formally grounded |

#### Patterns for F04 — Anchor Provenance Metadata

| ID | Pattern | Source Domain(s) | Mechanism | Implementation Hint | Confidence |
|----|---------|-----------------|-----------|---------------------|------------|
| P15 | FHIR-style provenance record | Medical records (HL7 FHIR) | Each anchor records: who (actor/speaker), what (the anchor), when (extraction turn, wall clock), why (extraction context — what prompted this fact to emerge), how (extraction method — DICE, manual, system). | Extend `PropositionNode` with: `extractionTurn`, `speakerRole`, `extractionMethod`, `extractionContext` (brief note or source message hash). Wire population in `ChatActions` and `SimulationExtractionService`. | high — FHIR Provenance is a mature, well-specified model |
| P16 | Version chain with immutable history | MVCC, Accounting | Every version of an anchor is preserved. The `SUPERSEDES` relationship forms a version chain. Each version has birth/death timestamps (bi-temporal). | Already partially implemented via `SUPERSEDES` relationship and bi-temporal validity. Ensure completeness: every supersession event creates a full snapshot of the pre-revision state, not just a diff. | high — already in progress |
| P17 | Retrospective vs prospective effectivity | Accounting (IAS 8), Legal | Record whether a revision is retrospective (the old fact was wrong from the start) or prospective (the old fact was correct at the time but is now changing). This affects how narrative history is interpreted. | Add `revisionType` field to supersession event: `RETROSPECTIVE` (error correction) vs `PROSPECTIVE` (legitimate change). RETROSPECTIVE revisions may trigger narrative retcon prompts; PROSPECTIVE revisions acknowledge the change happened at this point in the narrative. | medium — useful for narrative consistency, not blocking for initial F04 |

#### Patterns for F05 — UI-Controlled Mutation

| ID | Pattern | Source Domain(s) | Mechanism | Implementation Hint | Confidence |
|----|---------|-----------------|-----------|---------------------|------------|
| P18 | MV-Register (present conflicts for human resolution) | CRDTs | When classification is uncertain (borderline REVISION vs CONTRADICTION), present both values to the user and let them choose. The UI becomes a conflict resolution interface. | In the anchor edit UI, when cascade evaluation identifies ambiguous dependents (interchangeability uncertain), present a "resolve conflicts" dialog showing the old and new states and letting the user decide per-dependent. | medium — good UX pattern but increases UI complexity |
| P19 | Protection levels per anchor | Wikipedia | Anchors have configurable edit protection levels (none / semi / full) independent of authority. This allows operators to lock specific anchors regardless of their authority level. | Add `editProtection` field to anchor (enum: OPEN / RESTRICTED / LOCKED). UI displays lock icon. RESTRICTED requires confirmation dialog; LOCKED requires CanonizationGate. This is the "pinned" concept extended with gradations. | medium — extends existing "pinned" concept with more granularity |

### Summary: Feature-to-Pattern Mapping

| Feature | High-Confidence Patterns | Key Sources |
|---------|------------------------|-------------|
| **F01** (Classification) | P01 (2-axis classification), P02 (causal ordering), P03 (author privilege) | ORES, CRDTs, Medical |
| **F02** (Compliance) | P06 (materiality-proportional process), P07 (authority-escalation gating) | Accounting, ECO, Medical |
| **F03** (Cascade) | P09 (JTMS label propagation), P10 (interchangeability), P11 (soft cascade), P13 (depth limiting), P14 (minimal change) | TMS, ECO, Medical, AGM |
| **F04** (Provenance) | P15 (FHIR provenance), P16 (version chain) | Medical/FHIR, MVCC |
| **F05** (UI Mutation) | P18 (MV-Register conflict presentation), P19 (protection levels) | CRDTs, Wikipedia |

## Impact

1. **Roadmap changes**:
   - F01 scope SHOULD expand to include the two-axis classification model (P01) rather than a flat 3-way enum. This is the single highest-impact finding.
   - F03 scope SHOULD incorporate interchangeability evaluation (P10) alongside the already-planned dependency detection. This prevents over-invalidation.
   - F02 scope SHOULD incorporate materiality-proportional gating (P06) rather than pure authority-level gating. This handles the "PROVISIONAL anchor with many dependents" edge case.

2. **Feature doc changes**:
   - F01 proposal seed should reference the ORES two-axis model [E14] and CRDT causal ordering [E07] for classification design.
   - F03 proposal seed should reference JTMS label propagation [E01, E15] for cascade algorithm design and ECO interchangeability analysis [E08] for cascade evaluation.
   - F02 proposal seed should reference accounting materiality threshold [E13] for authority-gating design.
   - F04 proposal seed should reference FHIR Provenance resource [E10] for provenance record design.

3. **Proposal scope changes**:
   - The JTMS cascade algorithm (P09) is sufficiently well-understood that F03 implementation risk is LOWER than initially estimated. The algorithm has 40+ years of implementation history.
   - The ORES two-axis model (P01) INCREASES F01 complexity slightly (2 dimensions instead of 1) but IMPROVES classification accuracy. Net: higher implementation cost, higher value.
   - The materiality concept (P06) may SIMPLIFY F02 by providing a single computed metric instead of multiple authority-level-specific rules.

## Remaining Gaps

1. **Empirical validation of NLP causal ordering detection (P02)**: The CRDT causal ordering concept is theoretically sound, but detecting causal reference in natural language (e.g., "Actually, change that to..." vs "He is not a wizard") requires empirical testing. R01 (Revision Intent Classification Accuracy) should evaluate this specifically.

2. **Interchangeability evaluation cost (P10)**: Per-dependent LLM evaluation for interchangeability could be expensive in deep dependency graphs. Need to assess: (a) typical dependency depth in practice, (b) whether heuristic interchangeability evaluation (keyword overlap, semantic similarity) is sufficient vs requiring LLM judgment.

3. **Circular dependency frequency (P12)**: Unknown how common circular anchor dependencies are in practice. If rare, well-founded semantics handling may be low priority. Simulation data from existing scenarios could answer this.

4. **ORES feature transferability (P01, P05)**: ORES features are tuned for Wikipedia's specific editing patterns. How well they transfer to natural-language-based anchor mutation in a conversational context is unknown. The feature categories (user reputation, edit characteristics, temporal features) are likely transferable, but specific feature engineering will differ.

5. **Retrospective vs prospective revision (P17)**: The distinction between "this was always wrong" and "this is changing now" has narrative implications (retcon vs in-story change) that need exploration. No existing domain provides a direct model for how to handle this in a narrative/conversational AI context.

6. **Web access limitations**: This research was conducted without access to WebSearch or WebFetch. All findings are based on established academic literature, standards, and documentation within the researcher's training knowledge. Specific details (e.g., ORES precision/recall numbers, HIPAA regulatory text) are drawn from well-known, widely-cited sources and are marked with confidence levels. No live verification against primary sources was possible. A follow-up pass with web access could confirm specific numerical claims and capture any recent developments (post-2025) in these domains.

7. **ATMS applicability**: The ATMS approach (maintaining all consistent environments simultaneously) is theoretically powerful for cascade evaluation but may be computationally prohibitive for real-time anchor management. The JTMS approach (single label state with propagation) is more practical. Whether hybrid approaches (ATMS-style environment tracking for high-value anchors only) are warranted is unexplored.
