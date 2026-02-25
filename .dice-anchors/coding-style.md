# Coding Style

## General

Java 25 with `--enable-preview`. Maven. Spring Boot 3.5.x. Embabel Agent 0.3.x.

Follow the style of the code you read. Favor clarity over novelty. This is a demo app — ruthlessly cut anything that doesn't serve understanding.

Do not include blank lines within methods without good reason.

- **Good reason**: Two groups of related statements, separated by a blank line to indicate distinct logical steps.
- **Bad reason**: One line, blank line, another line for no particular reason.

Do not put in license headers — the build handles that.

## Comments

Comment only non-obvious logic. Code is self-documenting via naming.

### Delete These Comments

| Pattern | Example | Why |
|---------|---------|-----|
| Section banners | `// ========== Helpers ==========` | Methods have names. |
| Dashed dividers | `// ----- Wiring -----` | Extract methods if sections are too long. |
| Field group labels | `// Controls`, `// State`, `// Progress` | Field names are self-documenting. |
| Restating the code | `// Set the name` above `setName(name)` | Reader can read. |
| Tautological `@param` | `@param contextId the context ID` | Says nothing the signature doesn't. |
| Tautological Javadoc | `/** Renders the report. */` on `render(Report r)` | Method name says this. |
| Orphaned spec refs | `// (10.7)`, `// per F06` | Meaningless without context. |
| Future enhancement notes | `// Future: support X` | Not a TODO, not a bug. |
| Getter/setter labels | `// --- Anchor getters ---` | IDE can fold. |

### Keep These Comments

- **Invariant annotations**: `A1: rank always in [100, 900]` — documents contracts
- **Non-obvious logic**: Lazy-init guards, algorithmic choices, framework quirks
- **Class-level Javadoc with real content**: Layout diagrams, state machine docs, design decisions
- **`@param`/`@return` with real semantics**: constraints, side effects, valid ranges — not just "the X"

## Defensive Checks

Trust the framework. Don't defend against things that can't happen.

### Delete These Checks

| Pattern | Why |
|---------|-----|
| Null check on framework values | Vaadin guarantees value if you set a default |
| `Objects.requireNonNull` on record fields | Use compact constructor or `@NonNull` |
| `Optional.ofNullable` on data you control | Assert instead of wrapping |
| Try-catch that just rethrows as RuntimeException | Let it propagate |
| Catch for impossible exceptions | Don't `catch (IOException e)` on classpath streams |
| Null-to-empty-string in internal methods | Fail-fast — let NPE surface bad data |

### Keep These Checks

- **Boundary validation**: User input, external API responses, deserialized data
- **Contract enforcement in public API**: `if (rank < 100 || rank > 900) throw` in `clampRank()`
- **`Optional` returns from repositories**: `findById()` legitimately returns `Optional`

## Java

**Default to modern.** The question isn't "is there a reason to modernize?" — it's "is there a reason NOT to?"

| Old pattern | Modern replacement |
|-------------|-------------------|
| `if (obj instanceof Foo) { Foo f = (Foo) obj; }` | `if (obj instanceof Foo f) { ... }` |
| `instanceof` + cast chain | `switch` with pattern matching: `case Foo f -> ...` |
| `switch` with breaks and fall-through | Switch expression: `return switch (x) { case A -> ...; };` |
| `if/else if/else` on type or enum | Exhaustive switch expression |
| `catch (Exception e) { /* ignored */ }` | `catch (Exception _)` (unnamed variable) |
| C-style `for (int i = 0; i < list.size(); i++)` | `for (var item : list)` unless index is needed |
| String concatenation chains | Text blocks `"""..."""` or `.formatted()` |
| `String.format()` | `"text %s".formatted(val)` (except log statements — use `{}`) |
| Mutable POJO with getters/setters | **Record.** Immutable data = record. No exceptions. |
| Abstract class with no shared state | Sealed interface with record implementations |
| `new ArrayList<>()` for literals | `List.of()`, `Set.of()`, `Map.of()` |
| `Collections.unmodifiableList(new ArrayList<>(...))` | `List.copyOf(source)` |
| `.collect(Collectors.toList())` | `.toList()` |
| `Optional.isPresent()` + `.get()` | `.ifPresent()`, `.map()`, or pattern match |

**Records are the default for data.** DTOs, view models, API responses, config carriers, `PromptContributor` implementations, `@LlmTool` return types, test fixtures.

**Sealed interfaces are the default for type hierarchies.** Fixed set of subtypes = seal the interface, use record implementations, get exhaustive switch checking.

**Don't force it.** If the old pattern is genuinely clearer, keep it. "I'm used to it" is not a reason.

Additional rules:
- `var` for local variables where type is obvious; explicit types for fields, parameters, return types
- Constructor injection only — never `@Autowired` on fields
- JSpecify nullness: `@Nullable` and `@NonNull` on fields and parameters
- Early returns over nested conditionals
- Unchecked exceptions only: `IllegalArgumentException`, `IllegalStateException`
- No wildcard imports — explicit imports only

## LLM Integration

Return structured data (records), not raw strings, when interacting with LLMs.

`PromptContributor` implementations are data carrier records, not service beans. Services do assembly; records carry the result.

```java
// RIGHT: data carrier
record AnchorContext(String text, List<String> anchorIds) implements PromptContributor {
    @Override
    public @NonNull String contribution() { return text; }
}

// RIGHT: service does the work
@Service
public class AnchorContextAssembler {
    public AnchorContext assembleContext(String contextId) { ... }
}
```

`@LlmTool` methods return records, not formatted strings:

```java
// WRONG
@LlmTool(description = "List active anchors")
public String listAnchors() { return "Active anchors:\n- [rank 500] ..."; }

// RIGHT
@LlmTool(description = "List active anchors")
public AnchorList listAnchors() { return new AnchorList(anchors); }
```

## Spring / Embabel / DICE Idioms

| Anti-pattern | Idiomatic |
|--------------|-----------|
| `@Autowired` on fields | Constructor injection |
| `@Component` PromptContributor with injected services | Record implementing `PromptContributor` with pre-assembled data |
| `@LlmTool` returning `String` | `@LlmTool` returning a record |
| Manual Cypher string concatenation | `@Query` with parameters |
| `new ArrayList<>()` as default | `List.of()` |
| Checked exceptions | `IllegalArgumentException`, `IllegalStateException` |
| Wildcard imports | Explicit imports only |
| `logger.info("x " + val)` | `logger.info("x {}", val)` |

## Testing

Tests exist to verify that anchors resist drift, authority upgrades work, conflicts resolve correctly, and the simulation engine produces meaningful results. Not to verify wiring.

### Delete Entirely

- **Reflection-based structural tests**: `getDeclaredFields()` to verify constants exist. If a constant is wrong, the app won't start.
- **Trivial enum tests**: Single-assertion tests on `CANON.previousLevel() == RELIABLE`. That's testing Java.
- **Tests that mock everything**: If every dependency is mocked and only `verify(mock).method()` is checked, it tests wiring, not behavior.
- **Constructor/getter tests**: "Object is created successfully." Records eliminate this category.
- **Path/resource existence tests**: If a template is missing, the app fails at startup.

### Fix, Don't Delete

- Tests with real assertions but poor structure: reorganize with `@Nested` + `@DisplayName`, rename to `actionConditionExpectedOutcome`
- Tests that test the right thing but have slop around them: remove banners, unnecessary comments, redundant setup

### Red Flags in Tests

| Smell | Action |
|-------|--------|
| Test name starts with `test` | Rename: `actionConditionExpectedOutcome` |
| Only assertion is `assertNotNull` | Delete — what behavior does it verify? |
| `@DisplayName` restates the method name | Remove or describe the behavior |
| Helper method with 0 callers | Delete |
| `@BeforeEach` sets up mocks unused in most tests | Move setup into the tests that need it |

### Keep

- JUnit 5 + Mockito + AssertJ
- `@Nested` + `@DisplayName` for structure
- Work test-first when fixing bugs: write failing test, then implement
- Don't couple tests tightly to implementation

## What NOT to Do

- Do not return null from service methods — use `Optional` or throw
- Do not build Cypher via string concatenation — use `@Query` with parameters
- Do not use mutable defaults — `List.of()` not `new ArrayList<>()`
- Do not create checked exceptions
- Do not add wildcard imports
- Do not use field-level `@Autowired`
- Do not test getters/setters
- Do not add comments that restate the code
- Do not add defensive null checks on framework-managed values
- Do not add features, refactor, or "improve" beyond what was asked
