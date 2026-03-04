## 1. Caffeine Dependency and Configuration

Foundation: add the Caffeine dependency and tiered-storage configuration record. No behavioral changes.

- [x] 1.1 Add Caffeine dependency to `pom.xml` — `com.github.ben-manes.caffeine:caffeine` with no explicit version tag (managed by Spring Boot BOM). Place in the dependency section alongside existing dependencies. (spec: "Caffeine dependency")
  - **File**: `pom.xml`

- [x] 1.2 Add `TieredStorageConfig` record to `DiceAnchorsProperties` — fields: `enabled` (boolean, default false), `maxCacheSize` (int, default 1000, @Positive), `ttlMinutes` (int, default 60, @Positive). Add as a `@NestedConfigurationProperty` field named `tieredStorage` on `DiceAnchorsProperties`. (spec: "Tiered storage configuration")
  - **File**: `src/main/java/dev/dunnam/diceanchors/DiceAnchorsProperties.java`

- [x] 1.3 Add tiered-storage defaults to `application.yml` — `dice-anchors.tiered-storage.enabled: false`, `max-cache-size: 1000`, `ttl-minutes: 60`. (spec: "Tiered storage configuration")
  - **File**: `src/main/resources/application.yml`

- [x] 1.4 Verify: `./mvnw clean compile -DskipTests` succeeds

## 2. AnchorCache Component

In-memory Caffeine cache for HOT-tier anchors.

- [x] 2.1 Create `AnchorCache` component in `persistence` package — Caffeine cache with composite key (`contextId:anchorId`), `get`, `getAll`, `put`, `putAll`, `invalidate`, `invalidateAll`, `isPopulated`, `stats` methods. Use `@ConditionalOnProperty(name = "dice-anchors.tiered-storage.enabled", havingValue = "true")`. Constructor injection of `DiceAnchorsProperties`. (spec: "AnchorCache component", design D2)
  - **File**: `src/main/java/dev/dunnam/diceanchors/persistence/AnchorCache.java` — new

- [x] 2.2 Add unit tests for `AnchorCache` — put/get round-trip, invalidate removes entry, invalidateAll clears context, separate contexts isolated, isPopulated tracks state, stats records hits/misses. (spec: "AnchorCache component" scenarios)
  - **File**: `src/test/java/dev/dunnam/diceanchors/persistence/AnchorCacheTest.java` — new

- [x] 2.3 Verify: `./mvnw test` — all existing + new tests pass

## 3. TieredAnchorRepository Decorator

Decorator around AnchorRepository with tier-aware query routing.

- [x] 3.1 Create `TieredAnchorRepository` in `persistence` package — constructor injection of `AnchorRepository`, `AnchorCache`, and `DiceAnchorsProperties`. `findActiveAnchorsForAssembly(contextId)` loads from Neo4j, classifies by tier using `MemoryTier.fromRank()`, caches HOT, returns HOT + WARM. `findAllTiersForContext(contextId)` delegates to repository for full access. Lazy cache population per design D3. `@ConditionalOnProperty` for tiered storage enabled. (spec: "TieredAnchorRepository decorator", design D1/D3)
  - **File**: `src/main/java/dev/dunnam/diceanchors/persistence/TieredAnchorRepository.java` — new

- [x] 3.2 Add unit tests for `TieredAnchorRepository` — HOT served from cache after population, WARM from repository, COLD excluded from assembly, all tiers returned for maintenance, lazy population on first access, fallback when disabled. Mock `AnchorRepository` and `AnchorCache`. (spec: "TieredAnchorRepository decorator" scenarios)
  - **File**: `src/test/java/dev/dunnam/diceanchors/persistence/TieredAnchorRepositoryTest.java` — new

- [x] 3.3 Verify: `./mvnw test` — all existing + new tests pass

## 4. Event-Driven Cache Invalidation

Lifecycle event listener for maintaining cache coherence.

- [x] 4.1 Create `TieredCacheInvalidator` in `persistence` package — Spring `@Component` with `@EventListener` for `AnchorLifecycleEvent`. Handles `TierChanged` (add/remove from cache on tier transitions), `Evicted`/`Archived` (remove from cache), `Promoted` (add if HOT), `Reinforced`/`AuthorityChanged` (update if in cache). Constructor injection of `AnchorCache`, `AnchorRepository`, `DiceAnchorsProperties`. No-op when tiered storage disabled. (spec: "TieredCacheInvalidator event listener", design D4)
  - **File**: `src/main/java/dev/dunnam/diceanchors/persistence/TieredCacheInvalidator.java` — new

- [x] 4.2 Add unit tests for `TieredCacheInvalidator` — promotion of HOT anchor adds to cache, eviction removes from cache, tier transition WARM-to-HOT adds to cache, tier transition HOT-to-WARM removes from cache, events ignored when disabled. Mock `AnchorCache` and `AnchorRepository`. (spec: "TieredCacheInvalidator event listener" scenarios)
  - **File**: `src/test/java/dev/dunnam/diceanchors/persistence/TieredCacheInvalidatorTest.java` — new

- [x] 4.3 Verify: `./mvnw test` — all existing + new tests pass

## 5. Assembly Integration

Wire tiered repository into prompt assembly path.

- [x] 5.1 Add `TieredAnchorRepository` parameter to `AnchorsLlmReference` — new constructor overload accepting `@Nullable TieredAnchorRepository`. In `loadBulkMode()` and `loadHybridMode()`, use `tieredRepository.findActiveAnchorsForAssembly()` when present, otherwise fall back to `engine.inject()`. (spec: "Assembly integration with tiered storage", design D5)
  - **File**: `src/main/java/dev/dunnam/diceanchors/assembly/AnchorsLlmReference.java`

- [x] 5.2 Wire `TieredAnchorRepository` into `ChatActions` — inject the optional bean and pass to `AnchorsLlmReference` construction. Use `@Autowired(required = false)` or `Optional<TieredAnchorRepository>` to handle disabled state.
  - **File**: `src/main/java/dev/dunnam/diceanchors/chat/ChatActions.java`

- [x] 5.3 Wire `TieredAnchorRepository` into `SimulationTurnExecutor` — inject the optional bean and pass to `AnchorsLlmReference` construction during simulation turns. Same pattern as ChatActions.
  - **File**: `src/main/java/dev/dunnam/diceanchors/sim/engine/SimulationTurnExecutor.java`

- [x] 5.4 Add observability logging — in `TieredAnchorRepository.findActiveAnchorsForAssembly()`, log tier distribution at DEBUG: HOT count, WARM count, COLD count (excluded). Log cache stats (hits/misses) when available. (spec: "Observability logging")
  - **File**: `src/main/java/dev/dunnam/diceanchors/persistence/TieredAnchorRepository.java`

- [x] 5.5 Verify: `./mvnw test` — all existing tests pass with tiered storage disabled (default)

## 6. Scenario YAML Integration

Enable tiered storage per-scenario for simulation.

- [x] 6.1 Add `tiered-storage` configuration option to scenario YAML schema — allow scenarios to opt in to tiered storage. The `ScenarioLoader` should recognize the configuration and pass it through to the simulation context. This is additive: existing scenarios without the key use the global default.
  - **File**: `src/main/java/dev/dunnam/diceanchors/sim/engine/ScenarioLoader.java` (read to assess)
  - **File**: `src/main/resources/simulations/` (read to assess YAML format)

- [x] 6.2 Verify: `./mvnw test` — all existing + new tests pass

## 7. Final Verification

- [x] 7.1 Run full test suite — `./mvnw test` — all existing + new tests pass (1010/1011; 1 pre-existing Prolog failure unrelated to this feature)
- [x] 7.2 Run full compile — `./mvnw clean compile` — no warnings, no errors
- [x] 7.3 Verify fallback mode — all existing tests pass with `tiered-storage.enabled=false` (the default). No behavioral changes for existing code paths.
- [x] 7.4 Verify no COLD anchors in assembly — `TieredAnchorRepository.findActiveAnchorsForAssembly()` filters COLD anchors at the source; only HOT + WARM returned
- [x] 7.5 Verify cache coherence — `TieredCacheInvalidator` tests confirm lifecycle events correctly update the cache (WARM→HOT adds, HOT→WARM removes, Evicted removes, Reinforced updates if cached)
