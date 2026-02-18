# dice-anchors Project Constitution

This constitution codifies the governing architectural constraints for the dice-anchors project. All specifications, proposals, and implementations MUST comply with these articles unless an explicit override is documented per the governance process below.

Keywords follow [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

---

## Article I: RFC 2119 Keyword Compliance

### Statement
All specifications, design documents, and requirement definitions MUST use RFC 2119 keywords (MUST, SHALL, SHOULD, MAY and their negations) to express requirement levels. Informal language ("should", "needs to", "has to") MUST NOT be used for normative requirements.

### Rationale
AI agents interpret specifications literally. Ambiguous requirement language leads to inconsistent implementation decisions. RFC 2119 keywords provide a shared, machine-interpretable vocabulary for requirement severity.

### Enforcement
- Spec review checks for RFC 2119 keyword presence in all requirement blocks
- Proposals without RFC 2119 keywords in requirements sections SHALL be rejected

### Override Process
Not applicable — this article governs spec format and cannot be overridden.

---

## Article II: Neo4j as Sole Persistence Authority

### Statement
Neo4j SHALL be the sole authoritative store for all persistent state: propositions, anchors, entities, relationships, and simulation data. There is no PostgreSQL in this project. All persistence SHALL go through Drivine ORM and the `AnchorRepository`.

### Rationale
dice-anchors is a single-purpose demo focused on anchor drift resistance. A single graph store simplifies the architecture and avoids the dual-store complexity of the parent tor project.

### Enforcement
- Code review verifies no relational database dependencies
- All persistence operations go through `AnchorRepository` or `GraphObjectManager`

### Override Process
Adding a second store requires documented justification and explicit constitution amendment.

---

## Article III: Constructor Injection Only

### Statement
All Spring-managed beans MUST use constructor injection. Field-level `@Autowired` annotations MUST NOT be used. Setter injection MUST NOT be used.

### Rationale
Constructor injection makes dependencies explicit, enables immutability, simplifies testing (no reflection needed), and fails fast on missing dependencies at startup rather than at runtime.

### Enforcement
- Code review rejects `@Autowired` on fields or setters

### Override Process
Requires documented justification. Acceptable only for circular dependency resolution where `@Lazy` is insufficient.

---

## Article IV: Records for Immutable Data

### Statement
Immutable data transfer objects, data carriers, and configuration bindings (`@ConfigurationProperties`) SHOULD use Java `record` types. Mutable class-based DTOs SHOULD NOT be introduced for new code.

### Rationale
Records provide immutability by default, auto-generated `equals`/`hashCode`/`toString`, and concise syntax. They align with the project's preference for immutable collections (`List.of()`, `Set.of()`, `Map.of()`).

### Enforcement
- Code review flags new `class`-based DTOs that could be records

### Override Process
Drivine/Neo4j node entities requiring mutable state are exempt. Document in `## Specification Overrides`.

---

## Article V: Anchor Invariants

### Statement
The anchor subsystem MUST enforce the following invariants:
- **A1**: Active anchor count per context SHALL NOT exceed `anchorBudget` (default 20). When exceeded, the lowest-ranked non-pinned anchor MUST be evicted.
- **A2**: Anchor rank values MUST be clamped to the range [100, 900] via `clampRank()`.
- **A3**: Anchor promotion from proposition to anchor MUST be explicit — auto-promotion MUST NOT occur.
- **A4**: Anchor authority levels (PROVISIONAL, UNRELIABLE, RELIABLE, CANON) MUST follow upgrade-only hierarchy. CANON MUST NOT be auto-assigned.

### Rationale
Anchors are the primary mechanism for persistent memory in drift resistance. Unbounded growth leads to context overflow; uncontrolled ranks cause priority inversion; auto-promotion creates unreliable facts.

### Enforcement
- Unit tests validate budget enforcement, rank clamping, and explicit promotion
- `AnchorEngine` enforces A1 on every activation
- `Anchor.clampRank()` enforces A2 at the model layer

### Override Process
Budget and rank bounds are safety-critical and SHOULD NOT be overridden without extensive testing.

---

## Article VI: Simulation Isolation

### Statement
Each simulation run MUST operate with an isolated `contextId` (format: `sim-{uuid}`). Simulation data MUST be cleaned up on completion or cancellation. Simulation runs MUST NOT contaminate each other or the chat context.

### Rationale
Isolation prevents cross-run data leakage and ensures reproducible simulation results.

### Enforcement
- `SimulationService` assigns unique contextId per run
- Finally block cleans up via `anchorRepository.clearByContext(contextId)`

### Override Process
Not applicable — isolation is fundamental to simulation correctness.

---

## Article VII: Test-First for Domain Logic

### Statement
New domain logic (services, state machines, policies, extractors) SHOULD have unit tests written before or alongside the implementation. Test method naming MUST follow the `actionConditionExpectedOutcome` pattern. Tests MUST use JUnit 5 with `@Nested`/`@DisplayName` for structure, Mockito for mocking, and AssertJ for assertions.

### Rationale
Domain logic drives anchor correctness. Untested state transitions, extraction pipelines, or ranking algorithms silently corrupt anchor state.

### Enforcement
- Code review checks for test coverage on new domain logic

### Override Process
Exploratory spikes and prototype code MAY skip tests initially, but MUST add tests before merging to main.

---

## Governance

### Precedence
This constitution supersedes individual specifications where conflicts arise. Specifications MAY add domain-specific constraints that are more restrictive than the constitution but MUST NOT relax constitutional requirements.

### Amendments
Amending this constitution REQUIRES:
1. Documented change rationale
2. Update to the affected article(s) in this document
3. Review of all specs that reference the amended article

### Specification Overrides
Individual proposals MAY override specific constitutional requirements by including a `## Specification Overrides` section containing:
1. The article number being overridden
2. The specific clause being relaxed
3. Justification for the override
4. Scope limitation (which components/files are affected)
5. Expiration condition (when the override should be revisited)

Overrides MUST be approved during proposal review and SHOULD be time-bounded.
