## Tasks

### Task 1: Move directory trees with git mv

Move all four source roots:
```
git mv arcmem-core/src/main/java/dev/dunnam/arcmem arcmem-core/src/main/java/dev/arcmem
git mv arcmem-core/src/test/java/dev/dunnam/arcmem arcmem-core/src/test/java/dev/arcmem
git mv arcmem-simulator/src/main/java/dev/dunnam/arcmem arcmem-simulator/src/main/java/dev/arcmem
git mv arcmem-simulator/src/test/java/dev/dunnam/arcmem arcmem-simulator/src/test/java/dev/arcmem
```

Remove empty `dev/dunnam/` parent directories.

### Task 2: Update package declarations and imports

In every `.java` file under both modules, replace:
- `package dev.dunnam.arcmem.` → `package dev.arcmem.`
- `import dev.dunnam.arcmem.` → `import dev.arcmem.`

### Task 3: Update POM files

- Parent `pom.xml`: `<groupId>dev.dunnam.arcmem</groupId>` → `<groupId>dev.arcmem</groupId>`
- `arcmem-simulator/pom.xml`: `<mainClass>dev.dunnam.arcmem.simulator.ArcMemApplication</mainClass>` → `<mainClass>dev.arcmem.simulator.ArcMemApplication</mainClass>`

### Task 4: Verify

- `./mvnw clean compile -DskipTests` — MUST succeed
- `./mvnw test` — all 348 tests MUST pass
- `grep -rn "dev\.dunnam" --include="*.java" --include="pom.xml" arcmem-core/ arcmem-simulator/ pom.xml` — MUST return zero hits
