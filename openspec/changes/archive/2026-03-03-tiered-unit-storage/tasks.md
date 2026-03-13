## 1. Caffeine Dependency and Configuration

Foundation: add the Caffeine dependency and tiered-storage configuration record. No behavioral changes.

- [x] 1.1 Add Caffeine dependency to `pom.xml` — `com.github.ben-manes.caffeine:caffeine` with no explicit version tag (managed by Spring Boot BOM). Place in the dependency section alongside existing dependencies. (spec: "Caffeine dependency")
  - **File**: `pom.xml`

- [x] 1.2 Add `TieredStorageConfig` record to `ArcMemProperties` — fields: `enabled` (boolean, default false), `maxCacheSize` (int, default 1000, @Positive), `ttlMinutes` (int, default 60, @Positive). Add as a `@NestedConfigurationProperty` field named `tieredStorage` on `ArcMemProperties`. (spec: "Tiered storage configuration")
  - **File**: `src/main/java/dev/dunnam/arcmem/ArcMemProperties.java`

- [x] 1.3 Add tiered-storage defaults to `application.yml` — `context units.tiered-storage.enabled: false`, `max-cache-size: 1000`, `ttl-minutes: 60`. (spec: "Tiered storage configuration")
  - **File**: `src/main/resources/application.yml`

- [x] 1.4 Verify: `./mvnw clean compile -DskipTests` succeeds

## 2. ContextUnitCache Component

In-memory Caffeine cache for HOT-tier context units.

- [x] 2.1 Create `ContextUnitCache` component in `persistence` package — Caffeine cache with composite key (`contextId:unitId`), `get`, `getAll`, `put`, `putAll`, `invalidate`, `invalidateAll`, `isPopulated`, `stats` methods. Use `@ConditionalOnProperty(name = "context units.tiered-storage.enabled", havingValue = "true")`. Constructor injection of `ArcMemProperties`. (spec: "ContextUnitCache component", design D2)
  - **File**: `src/main/java/dev/dunnam/arcmem/persistence/ContextUnitCache.java` — new

- [x] 2.2 Add unit tests for `ContextUnitCache` — put/get round-trip, invalidate removes entry, invalidateAll clears context, separate contexts isolated, isPopulated tracks state, stats records hits/misses. (spec: "ContextUnitCache component" scenarios)
  - **File**: `src/test/java/dev/dunnam/arcmem/persistence/ContextUnitCacheTest.java` — new

- [x] 2.3 Verify: `./mvnw test` — all existing + new tests pass

## 3. TieredContextUnitRepository Decorator

Decorator around ContextUnitRepository with tier-aware query routing.

- [x] 3.1 Create `TieredContextUnitRepository` in `persistence` package — constructor injection of `ContextUnitRepository`, `ContextUnitCache`, and `ArcMemProperties`. `findActiveUnitsForAssembly(contextId)` loads from Neo4j, classifies by tier using `MemoryTier.fromRank()`, caches HOT, returns HOT + WARM. `findAllTiersForContext(contextId)` delegates to repository for full access. Lazy cache population per design D3. `@ConditionalOnProperty` for tiered storage enabled. (spec: "TieredContextUnitRepository decorator", design D1/D3)
  - **File**: `src/main/java/dev/dunnam/arcmem/persistence/TieredContextUnitRepository.java` — new

- [x] 3.2 Add unit tests for `TieredContextUnitRepository` — HOT served from cache after population, WARM from repository, COLD excluded from assembly, all tiers returned for maintenance, lazy population on first access, fallback when disabled. Mock `ContextUnitRepository` and `ContextUnitCache`. (spec: "TieredContextUnitRepository decorator" scenarios)
  - **File**: `src/test/java/dev/dunnam/arcmem/persistence/TieredContextUnitRepositoryTest.java` — new

- [x] 3.3 Verify: `./mvnw test` — all existing + new tests pass

## 4. Event-Driven Cache Invalidation

Lifecycle event listener for maintaining cache coherence.

- [x] 4.1 Create `TieredCacheInvalidator` in `persistence` package — Spring `@Component` with `@EventListener` for `ContextUnitLifecycleEvent`. Handles `TierChanged` (add/remove from cache on tier transitions), `Evicted`/`Archived` (remove from cache), `Promoted` (add if HOT), `Reinforced`/`AuthorityChanged` (update if in cache). Constructor injection of `ContextUnitCache`, `ContextUnitRepository`, `ArcMemProperties`. No-op when tiered storage disabled. (spec: "TieredCacheInvalidator event listener", design D4)
  - **File**: `src/main/java/dev/dunnam/arcmem/persistence/TieredCacheInvalidator.java` — new

- [x] 4.2 Add unit tests for `TieredCacheInvalidator` — promotion of HOT context unit adds to cache, eviction removes from cache, tier transition WARM-to-HOT adds to cache, tier transition HOT-to-WARM removes from cache, events ignored when disabled. Mock `ContextUnitCache` and `ContextUnitRepository`. (spec: "TieredCacheInvalidator event listener" scenarios)
  - **File**: `src/test/java/dev/dunnam/arcmem/persistence/TieredCacheInvalidatorTest.java` — new

- [x] 4.3 Verify: `./mvnw test` — all existing + new tests pass

## 5. Assembly Integration

Wire tiered repository into prompt assembly path.

- [x] 5.1 Add `TieredContextUnitRepository` parameter to `ArcMemLlmReference` — new constructor overload accepting `@Nullable TieredContextUnitRepository`. In `loadBulkMode()` and `loadHybridMode()`, use `tieredRepository.findActiveUnitsForAssembly()` when present, otherwise fall back to `engine.inject()`. (spec: "Assembly integration with tiered storage", design D5)
  - **File**: `src/main/java/dev/dunnam/arcmem/assembly/ArcMemLlmReference.java`

- [x] 5.2 Wire `TieredContextUnitRepository` into `ChatActions` — inject the optional bean and pass to `ArcMemLlmReference` construction. Use `@Autowired(required = false)` or `Optional<TieredContextUnitRepository>` to handle disabled state.
  - **File**: `src/main/java/dev/dunnam/arcmem/chat/ChatActions.java`

- [x] 5.3 Wire `TieredContextUnitRepository` into `SimulationTurnExecutor` — inject the optional bean and pass to `ArcMemLlmReference` construction during simulation turns. Same pattern as ChatActions.
  - **File**: `src/main/java/dev/dunnam/arcmem/sim/engine/SimulationTurnExecutor.java`

- [x] 5.4 Add observability logging — in `TieredContextUnitRepository.findActiveUnitsForAssembly()`, log tier distribution at DEBUG: HOT count, WARM count, COLD count (excluded). Log cache stats (hits/misses) when available. (spec: "Observability logging")
  - **File**: `src/main/java/dev/dunnam/arcmem/persistence/TieredContextUnitRepository.java`

- [x] 5.5 Verify: `./mvnw test` — all existing tests pass with tiered storage disabled (default)

## 6. Scenario YAML Integration

Enable tiered storage per-scenario for simulation.

- [x] 6.1 Add `tiered-storage` configuration option to scenario YAML schema — allow scenarios to opt in to tiered storage. The `ScenarioLoader` should recognize the configuration and pass it through to the simulation context. This is additive: existing scenarios without the key use the global default.
  - **File**: `src/main/java/dev/dunnam/arcmem/sim/engine/ScenarioLoader.java` (read to assess)
  - **File**: `src/main/resources/simulations/` (read to assess YAML format)

- [x] 6.2 Verify: `./mvnw test` — all existing + new tests pass

## 7. Final Verification

- [x] 7.1 Run full test suite — `./mvnw test` — all existing + new tests pass (1010/1011; 1 pre-existing Prolog failure unrelated to this feature)
- [x] 7.2 Run full compile — `./mvnw clean compile` — no warnings, no errors
- [x] 7.3 Verify fallback mode — all existing tests pass with `tiered-storage.enabled=false` (the default). No behavioral changes for existing code paths.
- [x] 7.4 Verify no COLD context units in assembly — `TieredContextUnitRepository.findActiveUnitsForAssembly()` filters COLD context units at the source; only HOT + WARM returned
- [x] 7.5 Verify cache coherence — `TieredCacheInvalidator` tests confirm lifecycle events correctly update the cache (WARM→HOT adds, HOT→WARM removes, Evicted removes, Reinforced updates if cached)
