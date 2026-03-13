## ADDED Requirements

### Requirement: Caffeine dependency

The project SHALL include `com.github.ben-manes.caffeine:caffeine` as a Maven dependency. The version MUST be managed by the Spring Boot BOM (3.2.3 for Spring Boot 3.5.10). No explicit `<version>` tag SHALL be specified in the POM.

#### Scenario: Caffeine available at compile time

- **GIVEN** the project's `pom.xml` includes the Caffeine dependency
- **WHEN** `./mvnw clean compile -DskipTests` is executed
- **THEN** compilation succeeds and `com.github.benmanes.caffeine.cache.Cache` is resolvable

### Requirement: ContextUnitCache component

The system SHALL provide an `ContextUnitCache` component in the `persistence` package that wraps a Caffeine cache for HOT-tier context unit storage. The cache MUST be keyed by a composite of `contextId` and `unitId`, storing `Context Unit` instances.

`ContextUnitCache` SHALL expose:
- `Optional<Context Unit> get(String contextId, String unitId)` — O(1) lookup
- `List<Context Unit> getAll(String contextId)` — all cached context units for a context
- `void put(String contextId, Context Unit context unit)` — insert or update a cached context unit
- `void putAll(String contextId, List<Context Unit> context units)` — bulk populate for a context
- `void invalidate(String contextId, String unitId)` — remove a single cached context unit
- `void invalidateAll(String contextId)` — remove all cached context units for a context
- `boolean isPopulated(String contextId)` — whether the cache has been populated for this context
- `CacheStats stats()` — Caffeine built-in hit/miss statistics

The cache MUST use `Caffeine.newBuilder().maximumSize(maxCacheSize).expireAfterWrite(ttlMinutes, MINUTES).recordStats()`.

#### Scenario: Put and get round-trip

- **GIVEN** an `ContextUnitCache` instance
- **WHEN** an context unit is stored via `put("ctx-1", context unit)` and retrieved via `get("ctx-1", unitId)`
- **THEN** the returned Optional contains the context unit with identical fields

#### Scenario: Invalidate removes entry

- **GIVEN** an `ContextUnitCache` with a cached context unit for context "ctx-1"
- **WHEN** `invalidate("ctx-1", unitId)` is called
- **THEN** `get("ctx-1", unitId)` returns `Optional.empty()`

#### Scenario: InvalidateAll clears context

- **GIVEN** an `ContextUnitCache` with 5 cached context units for context "ctx-1"
- **WHEN** `invalidateAll("ctx-1")` is called
- **THEN** `getAll("ctx-1")` returns an empty list and `isPopulated("ctx-1")` returns false

#### Scenario: Separate contexts are isolated

- **GIVEN** context units cached for context "ctx-1" and context "ctx-2"
- **WHEN** `invalidateAll("ctx-1")` is called
- **THEN** context units for "ctx-2" are unaffected

### Requirement: TieredContextUnitRepository decorator

The system SHALL provide a `TieredContextUnitRepository` in the `persistence` package that decorates `ContextUnitRepository` with tier-aware query routing. When tiered storage is enabled:

1. `findActiveUnitsForAssembly(String contextId)` MUST return only HOT + WARM context units. HOT context units SHALL be served from `ContextUnitCache`; WARM context units SHALL be loaded from Neo4j. COLD context units MUST NOT be included.
2. `findAllTiersForContext(String contextId)` MUST return ALL context units (HOT + WARM + COLD) for maintenance sweep access.
3. On first tiered access for a context, the repository SHALL populate the `ContextUnitCache` with HOT-tier context units from the Neo4j result (lazy population per prep decision O4).

When tiered storage is disabled (`tiered-storage.enabled=false`), `findActiveUnitsForAssembly()` SHALL delegate directly to `ContextUnitRepository.findActiveUnitsByContext()` without filtering, preserving current behavior.

`TieredContextUnitRepository` SHALL compute tier classification using `MemoryTier.fromRank(rank, hotThreshold, warmThreshold)` with thresholds from `ArcMemProperties.TierConfig`.

#### Scenario: HOT context units served from cache

- **GIVEN** tiered storage is enabled and the cache has been populated for context "ctx-1"
- **AND** context unit "A1" has rank 700 (HOT tier, hotThreshold=600)
- **WHEN** `findActiveUnitsForAssembly("ctx-1")` is called
- **THEN** context unit "A1" is returned from the cache without a Neo4j query

#### Scenario: WARM context units served from Neo4j

- **GIVEN** tiered storage is enabled
- **AND** context unit "A2" has rank 400 (WARM tier, hotThreshold=600, warmThreshold=350)
- **WHEN** `findActiveUnitsForAssembly("ctx-1")` is called
- **THEN** context unit "A2" is included in the result, loaded from Neo4j

#### Scenario: COLD context units excluded from assembly

- **GIVEN** tiered storage is enabled
- **AND** context unit "A3" has rank 200 (COLD tier, warmThreshold=350)
- **WHEN** `findActiveUnitsForAssembly("ctx-1")` is called
- **THEN** context unit "A3" is NOT included in the result

#### Scenario: All tiers returned for maintenance

- **GIVEN** tiered storage is enabled with HOT, WARM, and COLD context units
- **WHEN** `findAllTiersForContext("ctx-1")` is called
- **THEN** all context units across all tiers are returned

#### Scenario: Fallback to uniform loading when disabled

- **GIVEN** tiered storage is disabled (`tiered-storage.enabled=false`)
- **WHEN** `findActiveUnitsForAssembly("ctx-1")` is called
- **THEN** all active context units are returned (same as `findActiveUnitsByContext()`)

#### Scenario: Lazy cache population on first access

- **GIVEN** tiered storage is enabled and the cache is empty for context "ctx-1"
- **WHEN** `findActiveUnitsForAssembly("ctx-1")` is called for the first time
- **THEN** all active context units are loaded from Neo4j, HOT-tier context units are cached, and the result excludes COLD context units

### Requirement: TieredCacheInvalidator event listener

The system SHALL provide a `TieredCacheInvalidator` Spring bean (`@Component`) that listens for `ContextUnitLifecycleEvent` instances and updates the `ContextUnitCache` accordingly. This component is separate from the existing `ContextUnitCacheInvalidator` (which tracks dirty context IDs for `ArcMemLlmReference`); this new component manages the HOT-tier Caffeine cache.

The following events MUST trigger cache updates:

| Event | Action |
|-------|--------|
| `Promoted` | If new context unit is HOT tier, add to cache |
| `Reinforced` | If context unit transitioned to HOT, add to cache; if still HOT, update cached entry |
| `Archived` | Remove from cache |
| `Evicted` | Remove from cache |
| `AuthorityChanged` | Update cached entry if present |
| `TierChanged` | If new tier is HOT, add to cache; if previous tier was HOT and new tier is not, remove from cache |

The `TieredCacheInvalidator` SHALL only process events when tiered storage is enabled.

#### Scenario: Promotion of HOT context unit adds to cache

- **GIVEN** tiered storage is enabled
- **WHEN** an context unit is promoted with rank 700 (HOT tier)
- **THEN** the `TieredCacheInvalidator` adds the context unit to the cache

#### Scenario: Eviction removes from cache

- **GIVEN** a HOT context unit is cached for context "ctx-1"
- **WHEN** an `Evicted` event fires for that context unit
- **THEN** the context unit is removed from the cache

#### Scenario: Tier transition WARM-to-HOT adds to cache

- **GIVEN** a WARM context unit not in the cache
- **WHEN** a `TierChanged` event fires with previousTier=WARM and newTier=HOT
- **THEN** the context unit is added to the cache

#### Scenario: Tier transition HOT-to-WARM removes from cache

- **GIVEN** a HOT context unit in the cache
- **WHEN** a `TierChanged` event fires with previousTier=HOT and newTier=WARM
- **THEN** the context unit is removed from the cache

#### Scenario: Events ignored when tiered storage disabled

- **GIVEN** tiered storage is disabled
- **WHEN** any `ContextUnitLifecycleEvent` fires
- **THEN** the `TieredCacheInvalidator` takes no action

### Requirement: Tiered storage configuration

`ArcMemProperties` SHALL include a new `TieredStorageConfig` record:

```java
public record TieredStorageConfig(
    @DefaultValue("false") boolean enabled,
    @Positive @DefaultValue("1000") int maxCacheSize,
    @Positive @DefaultValue("60") int ttlMinutes
) {}
```

The configuration key prefix MUST be `context units.tiered-storage`.

`application.yml` SHALL include defaults:

```yaml
context units:
  tiered-storage:
    enabled: false
    max-cache-size: 1000
    ttl-minutes: 60
```

#### Scenario: Feature disabled by default

- **GIVEN** no explicit `tiered-storage` configuration
- **WHEN** the application starts
- **THEN** `tieredStorage.enabled()` returns `false`

#### Scenario: Feature enabled via configuration

- **GIVEN** `context units.tiered-storage.enabled=true` in application properties
- **WHEN** the application starts
- **THEN** `tieredStorage.enabled()` returns `true` and tiered routing is active

### Requirement: Assembly integration with tiered storage

When tiered storage is enabled, `ArcMemLlmReference` SHALL use `TieredContextUnitRepository.findActiveUnitsForAssembly()` instead of `ArcMemEngine.inject()` to load context units. This ensures COLD context units are excluded from prompt assembly and HOT context units are served from cache.

When tiered storage is disabled, `ArcMemLlmReference` SHALL continue to use `ArcMemEngine.inject()` as the data source (current behavior preserved).

#### Scenario: Prompt assembly excludes COLD context units

- **GIVEN** tiered storage is enabled
- **AND** context "ctx-1" has 3 HOT context units, 5 WARM context units, and 2 COLD context units
- **WHEN** `ArcMemLlmReference.getContent()` is called
- **THEN** the rendered prompt includes only the 3 HOT + 5 WARM context units (8 total)

#### Scenario: Prompt assembly unchanged when disabled

- **GIVEN** tiered storage is disabled
- **WHEN** `ArcMemLlmReference.getContent()` is called
- **THEN** all active context units are included (same as current behavior)

### Requirement: Observability logging

The system SHALL log the following structured information when tiered storage is enabled:

1. Cache hit/miss rates: logged at DEBUG level per prompt assembly call with `cache.hits` and `cache.misses` from Caffeine stats.
2. Tier distribution: logged at DEBUG level per prompt assembly call with `tier.hot.count`, `tier.warm.count`, `tier.cold.count`.
3. Cache invalidation: logged at DEBUG level with `cache.invalidate.reason` and `cache.invalidate.unitId`.

#### Scenario: Tier distribution logged

- **GIVEN** tiered storage is enabled
- **WHEN** prompt assembly completes for a context
- **THEN** a log entry at DEBUG level includes the count of HOT, WARM, and COLD context units
