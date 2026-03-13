## Approach

Mechanical find-and-replace of `dev.dunnam.arcmem` → `dev.arcmem` across all source files, test files, and build configuration. No architectural changes.

## Implementation Strategy

### Phase 1: Directory structure

Move source trees from `dev/dunnam/arcmem/` to `dev/arcmem/` in all four source roots:
- `arcmem-core/src/main/java/`
- `arcmem-core/src/test/java/`
- `arcmem-simulator/src/main/java/`
- `arcmem-simulator/src/test/java/`

Use `git mv` to preserve history.

### Phase 2: Package declarations and imports

Update every `.java` file:
- `package dev.dunnam.arcmem.` → `package dev.arcmem.`
- `import dev.dunnam.arcmem.` → `import dev.arcmem.`

### Phase 3: Build configuration

- Parent POM `groupId`: `dev.dunnam.arcmem` → `dev.arcmem`
- Child POMs inherit `groupId` from parent
- `arcmem-simulator/pom.xml` `mainClass`: `dev.dunnam.arcmem.simulator.ArcMemApplication` → `dev.arcmem.simulator.ArcMemApplication`

### Phase 4: Verify

- `./mvnw clean compile -DskipTests`
- `./mvnw test`
- Grep for any remaining `dev.dunnam` references

## Risks

- **Low**: Purely mechanical. No logic changes, no API changes, no behavior changes.
- The only risk is a missed reference — mitigated by comprehensive grep verification.
