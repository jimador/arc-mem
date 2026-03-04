## ADDED Requirements

### Requirement: Caffeine dependency

The project SHALL include `com.github.ben-manes.caffeine:caffeine` as a Maven dependency. The version MUST be managed by the Spring Boot BOM (3.2.3 for Spring Boot 3.5.10). No explicit `<version>` tag SHALL be specified in the POM.

#### Scenario: Caffeine available at compile time

- **GIVEN** the project's `pom.xml` includes the Caffeine dependency
- **WHEN** `./mvnw clean compile -DskipTests` is executed
- **THEN** compilation succeeds and `com.github.benmanes.caffeine.cache.Cache` is resolvable

### Requirement: AnchorCache component

The system SHALL provide an `AnchorCache` component in the `persistence` package that wraps a Caffeine cache for HOT-tier anchor storage. The cache MUST be keyed by a composite of `contextId` and `anchorId`, storing `Anchor` instances.

`AnchorCache` SHALL expose:
- `Optional<Anchor> get(String contextId, String anchorId)` — O(1) lookup
- `List<Anchor> getAll(String contextId)` — all cached anchors for a context
- `void put(String contextId, Anchor anchor)` — insert or update a cached anchor
- `void putAll(String contextId, List<Anchor> anchors)` — bulk populate for a context
- `void invalidate(String contextId, String anchorId)` — remove a single cached anchor
- `void invalidateAll(String contextId)` — remove all cached anchors for a context
- `boolean isPopulated(String contextId)` — whether the cache has been populated for this context
- `CacheStats stats()` — Caffeine built-in hit/miss statistics

The cache MUST use `Caffeine.newBuilder().maximumSize(maxCacheSize).expireAfterWrite(ttlMinutes, MINUTES).recordStats()`.

#### Scenario: Put and get round-trip

- **GIVEN** an `AnchorCache` instance
- **WHEN** an anchor is stored via `put("ctx-1", anchor)` and retrieved via `get("ctx-1", anchorId)`
- **THEN** the returned Optional contains the anchor with identical fields

#### Scenario: Invalidate removes entry

- **GIVEN** an `AnchorCache` with a cached anchor for context "ctx-1"
- **WHEN** `invalidate("ctx-1", anchorId)` is called
- **THEN** `get("ctx-1", anchorId)` returns `Optional.empty()`

#### Scenario: InvalidateAll clears context

- **GIVEN** an `AnchorCache` with 5 cached anchors for context "ctx-1"
- **WHEN** `invalidateAll("ctx-1")` is called
- **THEN** `getAll("ctx-1")` returns an empty list and `isPopulated("ctx-1")` returns false

#### Scenario: Separate contexts are isolated

- **GIVEN** anchors cached for context "ctx-1" and context "ctx-2"
- **WHEN** `invalidateAll("ctx-1")` is called
- **THEN** anchors for "ctx-2" are unaffected

### Requirement: TieredAnchorRepository decorator

The system SHALL provide a `TieredAnchorRepository` in the `persistence` package that decorates `AnchorRepository` with tier-aware query routing. When tiered storage is enabled:

1. `findActiveAnchorsForAssembly(String contextId)` MUST return only HOT + WARM anchors. HOT anchors SHALL be served from `AnchorCache`; WARM anchors SHALL be loaded from Neo4j. COLD anchors MUST NOT be included.
2. `findAllTiersForContext(String contextId)` MUST return ALL anchors (HOT + WARM + COLD) for maintenance sweep access.
3. On first tiered access for a context, the repository SHALL populate the `AnchorCache` with HOT-tier anchors from the Neo4j result (lazy population per prep decision O4).

When tiered storage is disabled (`tiered-storage.enabled=false`), `findActiveAnchorsForAssembly()` SHALL delegate directly to `AnchorRepository.findActiveAnchorsByContext()` without filtering, preserving current behavior.

`TieredAnchorRepository` SHALL compute tier classification using `MemoryTier.fromRank(rank, hotThreshold, warmThreshold)` with thresholds from `DiceAnchorsProperties.TierConfig`.

#### Scenario: HOT anchors served from cache

- **GIVEN** tiered storage is enabled and the cache has been populated for context "ctx-1"
- **AND** anchor "A1" has rank 700 (HOT tier, hotThreshold=600)
- **WHEN** `findActiveAnchorsForAssembly("ctx-1")` is called
- **THEN** anchor "A1" is returned from the cache without a Neo4j query

#### Scenario: WARM anchors served from Neo4j

- **GIVEN** tiered storage is enabled
- **AND** anchor "A2" has rank 400 (WARM tier, hotThreshold=600, warmThreshold=350)
- **WHEN** `findActiveAnchorsForAssembly("ctx-1")` is called
- **THEN** anchor "A2" is included in the result, loaded from Neo4j

#### Scenario: COLD anchors excluded from assembly

- **GIVEN** tiered storage is enabled
- **AND** anchor "A3" has rank 200 (COLD tier, warmThreshold=350)
- **WHEN** `findActiveAnchorsForAssembly("ctx-1")` is called
- **THEN** anchor "A3" is NOT included in the result

#### Scenario: All tiers returned for maintenance

- **GIVEN** tiered storage is enabled with HOT, WARM, and COLD anchors
- **WHEN** `findAllTiersForContext("ctx-1")` is called
- **THEN** all anchors across all tiers are returned

#### Scenario: Fallback to uniform loading when disabled

- **GIVEN** tiered storage is disabled (`tiered-storage.enabled=false`)
- **WHEN** `findActiveAnchorsForAssembly("ctx-1")` is called
- **THEN** all active anchors are returned (same as `findActiveAnchorsByContext()`)

#### Scenario: Lazy cache population on first access

- **GIVEN** tiered storage is enabled and the cache is empty for context "ctx-1"
- **WHEN** `findActiveAnchorsForAssembly("ctx-1")` is called for the first time
- **THEN** all active anchors are loaded from Neo4j, HOT-tier anchors are cached, and the result excludes COLD anchors

### Requirement: TieredCacheInvalidator event listener

The system SHALL provide a `TieredCacheInvalidator` Spring bean (`@Component`) that listens for `AnchorLifecycleEvent` instances and updates the `AnchorCache` accordingly. This component is separate from the existing `AnchorCacheInvalidator` (which tracks dirty context IDs for `AnchorsLlmReference`); this new component manages the HOT-tier Caffeine cache.

The following events MUST trigger cache updates:

| Event | Action |
|-------|--------|
| `Promoted` | If new anchor is HOT tier, add to cache |
| `Reinforced` | If anchor transitioned to HOT, add to cache; if still HOT, update cached entry |
| `Archived` | Remove from cache |
| `Evicted` | Remove from cache |
| `AuthorityChanged` | Update cached entry if present |
| `TierChanged` | If new tier is HOT, add to cache; if previous tier was HOT and new tier is not, remove from cache |

The `TieredCacheInvalidator` SHALL only process events when tiered storage is enabled.

#### Scenario: Promotion of HOT anchor adds to cache

- **GIVEN** tiered storage is enabled
- **WHEN** an anchor is promoted with rank 700 (HOT tier)
- **THEN** the `TieredCacheInvalidator` adds the anchor to the cache

#### Scenario: Eviction removes from cache

- **GIVEN** a HOT anchor is cached for context "ctx-1"
- **WHEN** an `Evicted` event fires for that anchor
- **THEN** the anchor is removed from the cache

#### Scenario: Tier transition WARM-to-HOT adds to cache

- **GIVEN** a WARM anchor not in the cache
- **WHEN** a `TierChanged` event fires with previousTier=WARM and newTier=HOT
- **THEN** the anchor is added to the cache

#### Scenario: Tier transition HOT-to-WARM removes from cache

- **GIVEN** a HOT anchor in the cache
- **WHEN** a `TierChanged` event fires with previousTier=HOT and newTier=WARM
- **THEN** the anchor is removed from the cache

#### Scenario: Events ignored when tiered storage disabled

- **GIVEN** tiered storage is disabled
- **WHEN** any `AnchorLifecycleEvent` fires
- **THEN** the `TieredCacheInvalidator` takes no action

### Requirement: Tiered storage configuration

`DiceAnchorsProperties` SHALL include a new `TieredStorageConfig` record:

```java
public record TieredStorageConfig(
    @DefaultValue("false") boolean enabled,
    @Positive @DefaultValue("1000") int maxCacheSize,
    @Positive @DefaultValue("60") int ttlMinutes
) {}
```

The configuration key prefix MUST be `dice-anchors.tiered-storage`.

`application.yml` SHALL include defaults:

```yaml
dice-anchors:
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

- **GIVEN** `dice-anchors.tiered-storage.enabled=true` in application properties
- **WHEN** the application starts
- **THEN** `tieredStorage.enabled()` returns `true` and tiered routing is active

### Requirement: Assembly integration with tiered storage

When tiered storage is enabled, `AnchorsLlmReference` SHALL use `TieredAnchorRepository.findActiveAnchorsForAssembly()` instead of `AnchorEngine.inject()` to load anchors. This ensures COLD anchors are excluded from prompt assembly and HOT anchors are served from cache.

When tiered storage is disabled, `AnchorsLlmReference` SHALL continue to use `AnchorEngine.inject()` as the data source (current behavior preserved).

#### Scenario: Prompt assembly excludes COLD anchors

- **GIVEN** tiered storage is enabled
- **AND** context "ctx-1" has 3 HOT anchors, 5 WARM anchors, and 2 COLD anchors
- **WHEN** `AnchorsLlmReference.getContent()` is called
- **THEN** the rendered prompt includes only the 3 HOT + 5 WARM anchors (8 total)

#### Scenario: Prompt assembly unchanged when disabled

- **GIVEN** tiered storage is disabled
- **WHEN** `AnchorsLlmReference.getContent()` is called
- **THEN** all active anchors are included (same as current behavior)

### Requirement: Observability logging

The system SHALL log the following structured information when tiered storage is enabled:

1. Cache hit/miss rates: logged at DEBUG level per prompt assembly call with `cache.hits` and `cache.misses` from Caffeine stats.
2. Tier distribution: logged at DEBUG level per prompt assembly call with `tier.hot.count`, `tier.warm.count`, `tier.cold.count`.
3. Cache invalidation: logged at DEBUG level with `cache.invalidate.reason` and `cache.invalidate.anchorId`.

#### Scenario: Tier distribution logged

- **GIVEN** tiered storage is enabled
- **WHEN** prompt assembly completes for a context
- **THEN** a log entry at DEBUG level includes the count of HOT, WARM, and COLD anchors
