# Coding Style

## General

Java 25. Maven. Spring Boot 3.5.x. Embabel Agent 0.3.x.

Follow the style of the code you read. Favor clarity.

Do not include blank lines within methods without good reason.

GOOD REASON: Two groups of related statements, separated by a blank line to indicate distinct logical steps.

BAD REASON: One line, blank line, another line for no particular reason.

Do not put in license headers as the build will handle that.

Do not comment obvious things, inline or in type headers. Comment only things that may be non-obvious. Variable, method, and type naming should be self-documenting as far as possible.

Use consistent naming in the Spring idiom. Use the Spring idiom where possible.

Favor immutability. Use `List.of()`, `Set.of()`, `Map.of()` for collection literals. Use records for immutable DTOs, tool containers (`@MatryoshkaTools`), and config (`@ConfigurationProperties`).

In log statements, use placeholders for efficiency at all logging levels. E.g. `logger.info("Created anchor {} in context {}", id, contextId)` instead of `logger.info("Created anchor %s in context %s".formatted(id, contextId))`.

Unless there is a specific reason not to, use the latest GA version of all dependencies.

## LLM Integration

Return structured data (records), not raw strings, when interacting with LLMs.

PromptContributors should be data carrier records, not service beans. Assembly logic belongs in the service layer; the contributor is just a record holding pre-assembled data. Services do the work, records carry the result.

WRONG: A singleton `@Component` that implements `PromptContributor` with injected services and thread-locals
RIGHT: A record that implements `PromptContributor` and carries pre-assembled data

```java
// Data carrier — no dependencies, no thread-locals
record AnchorContext(String text, List<String> anchorIds) implements PromptContributor {
    @Override
    public @NonNull String contribution() { return text; }
}

// Service — does the assembly
@Service
public class AnchorContextAssembler {
    public AnchorContext assembleContext(String contextId) { ... }
}
```

`@LlmTool` methods should return records, not formatted strings. Let the caller decide how to present the data.

```java
// WRONG
@LlmTool(description = "List active anchors")
public String listAnchors() {
    return "Active anchors:\n- [rank 500] The king is dead\n";
}

// RIGHT
@LlmTool(description = "List active anchors")
public AnchorList listAnchors() {
    return new AnchorList(anchors);
}
```

## Java

- Use modern Java features: `var`, records, switch expressions, sealed interfaces, multiline strings
- Use `var` for local variables where type is obvious; explicit types for fields, parameters, and return types
- Use `String.formatted()` for interpolation (except in log statements -- use placeholders there)
- Use multiline strings (`"""`) rather than concatenation chains
- Constructor injection only -- never `@Autowired` on fields
- JSpecify nullness: `@Nullable` and `@NonNull` on fields and parameters
- Early returns over nested conditionals
- Unchecked exceptions only: `IllegalArgumentException`, `IllegalStateException`; no checked exceptions
- No wildcard imports -- explicit imports only
- Builders and withers are both acceptable

WRONG: `String s = "a";`
RIGHT: `var s = "a";`

WRONG: `@Autowired private FooService fooService;`
RIGHT: Constructor parameter injection

WRONG: `logger.info("Processing %s".formatted(id));`
RIGHT: `logger.info("Processing {}", id);`

## Testing

- JUnit 5 + Mockito + AssertJ
- Use `@Nested` + `@DisplayName` for test structure
- Method naming: `actionConditionExpectedOutcome` (no `test` prefix)
- Do not couple tests too tightly to implementation
- Work test first when fixing bugs or adding features: write failing tests, then implement

## What NOT to Do

- Do not return null from service methods -- use `Optional` or throw
- Do not build SQL/Cypher via string concatenation -- use `@Query` with parameters
- Do not use mutable defaults -- `List.of()` not `new ArrayList<>()`
- Do not create checked exceptions
- Do not add wildcard imports
- Do not use field-level `@Autowired`
