# Contributing to dice-anchors

Thanks for your interest in contributing! This is a demo repository focused on clarity and teachability, so we keep the contribution process lightweight.

## Prerequisites

- **Java 25** or later
- **Docker** (for Neo4j)
- **Maven 3.9+**
- **OpenAI API key** (for running and testing locally)

## Build & Run

```bash
# Start Neo4j in the background
docker-compose up -d

# Build (skip tests)
./mvnw.cmd clean compile -DskipTests

# Run all tests (390+ total)
./mvnw.cmd test

# Run the app
OPENAI_API_KEY=sk-... ./mvnw.cmd spring-boot:run

# Then visit:
# - Simulation view:  http://localhost:8089
# - Chat view:        http://localhost:8089/chat
# - Neo4j browser:    http://localhost:7474 (neo4j/diceanchors123)
```

## Before Committing

**All tests must pass**:

```bash
./mvnw.cmd test
```

If any test fails, fix it before committing. If you add new functionality, write a test for it (see "Writing Tests" below).

**Build must succeed**:

```bash
./mvnw.cmd clean compile -DskipTests
```

## Coding Style

Follow these conventions (detailed rules in CLAUDE.md):

- **Constructor injection only** — never use `@Autowired` on fields. Spring should inject via the constructor.
  ```java
  @Service
  public class MyService {
    private final Dependency dep;

    public MyService(Dependency dep) { this.dep = dep; } // Good

    // @Autowired private Dependency dep; // Bad
  }
  ```

- **Records for immutable data** — use `record` for DTOs, tool containers, and config.
  ```java
  public record AnchorSnapshot(String id, int rank, Authority authority) { }
  ```

- **`var` for locals, explicit types elsewhere** — use `var` when type is obvious, but always be explicit for fields, parameters, and return types.
  ```java
  var count = anchors.size(); // OK: type is obvious
  int maxRank = 900;           // Good: field is explicit
  ```

- **Immutable collections** — use `List.of()`, `Set.of()`, `Map.of()` instead of `new ArrayList<>()`, etc.
  ```java
  List<String> names = List.of("Alice", "Bob");    // Good
  Set<String> tags = new HashSet<>(data);          // Avoid
  ```

- **No wildcard imports** — import specific classes.
  ```java
  import java.util.List;        // Good
  import java.util.*;           // Bad
  ```

- **Enum-driven state machines** — use enums (like `TurnType`, `Authority`) for lifecycle states.

- **Javadoc for domain invariants** — document constraints on domain classes (e.g., rank range, authority transitions).
  ```java
  /**
   * Anchor with rank [100, 900].
   * Authority progresses PROVISIONAL → UNRELIABLE → RELIABLE → CANON.
   * Invariant: count never exceeds budget (max 20).
   */
  public record Anchor(...) { }
  ```

- **Minimal inline comments** — code should be self-documenting via clear naming. Comment only non-obvious logic.
  ```java
  // Good: method name explains what it does
  boolean isAnchorStale() { }

  // Avoid: obvious from code, doesn't need comment
  if (count > max) { // count exceeded max
  ```

## Writing Tests

Tests use **JUnit 5** + **Mockito** + **AssertJ**. Follow the existing pattern:

```java
@DisplayName("AnchorEngine")
class AnchorEngineTest {

  @Nested
  @DisplayName("when promoting a proposition")
  class WhenPromoting {

    @Test
    @DisplayName("assigns rank between min and max")
    void assignsRankBetweenMinAndMax() {
      // Arrange
      var proposition = new Proposition(...);

      // Act
      var anchor = engine.promote(proposition);

      // Assert
      assertThat(anchor.rank()).isBetween(100, 900);
    }
  }
}
```

**Key patterns**:
- Use `@Nested` + `@DisplayName` for test structure
- Method names follow: `actionConditionExpectedOutcome` (no "test" prefix)
- Arrange/Act/Assert pattern
- Use AssertJ for assertions (`assertThat()`)
- Mock dependencies with Mockito

**Run tests**:
```bash
./mvnw.cmd test
```

Integration tests (`*IT.java` classes tagged with `@Tag("integration")`) are excluded by default.

## Adding Features

dice-anchors uses **OpenSpec** (spec-driven development). If you're adding a feature:

1. **Create a change proposal**:
   ```bash
   /opsx:new
   ```
   Describe what you're building and why.

2. **Write a spec**:
   ```bash
   /opsx:continue
   ```
   Specify the behavior, design, and acceptance criteria.

3. **Plan implementation**:
   ```bash
   /opsx:continue
   ```
   Create tasks for implementation work.

4. **Implement**:
   ```bash
   /opsx:apply
   ```
   Work through the tasks. Code as normal, commit incrementally.

5. **Verify**:
   ```bash
   /opsx:verify
   ```
   Confirm your code matches the spec.

6. **Archive**:
   ```bash
   /opsx:archive
   ```
   Close the change when complete.

See **CLAUDE.md** → "OpenSpec Workflow" for full details.

## Common Tasks

### Adding a New Simulation Scenario

1. Create `src/main/resources/simulations/your-scenario.yml`
2. Follow the structure of existing scenarios (e.g., `anchor-drift.yml`)
3. Define initial anchors, ground truth, personas, adversary config, and turn sequence
4. Load it via the SimulationView UI and test

### Adding an Attack Strategy

1. Add strategy metadata to `src/main/resources/simulations/strategy-catalog.yml`
2. Create/update prompt templates in `src/main/resources/prompts/`
3. Add new template path constants to `src/main/java/dev/dunnam/diceanchors/prompt/PromptPathConstants.java` (if using new templates)
4. Test via the "Adaptive" adversary mode in a scenario

### Adding a Test

```java
@DisplayName("MyClass")
class MyClassTest {
  // ... your test methods
}
```

Run with `./mvnw.cmd test`.

### Extending AnchorEngine

1. Add the new logic to `src/main/java/dev/dunnam/diceanchors/anchor/AnchorEngine.java`
2. Write a test in `src/test/java/.../anchor/AnchorEngineTest.java`
3. Follow the RFC 2119 keywords in CLAUDE.md (MUST, SHOULD, MAY)
4. Document any state invariants with Javadoc

## Pull Request Process

1. **Create a branch** for your feature
2. **Commit regularly** with clear commit messages
3. **Run tests** locally before pushing: `./mvnw.cmd test`
4. **Create a pull request** against `main`
5. **Address any feedback** and re-test
6. **Merge** when approved

## Questions?

- See **README.md** for project overview and quick start
- See **CLAUDE.md** for detailed architecture, coding style, and design decisions
- See **CHECKLIST.md** for refactor progress and clarity improvements
- Check existing code for patterns (especially `anchor/` and `sim/engine/`)

---

**Remember**: This is a DEMO repository. Prioritize clarity and teaching value over absolute perfection. If something is unclear, improve the documentation.
