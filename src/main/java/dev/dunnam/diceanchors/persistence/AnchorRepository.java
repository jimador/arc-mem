package dev.dunnam.diceanchors.persistence;

import com.embabel.agent.rag.service.RetrievableIdentifier;
import dev.dunnam.diceanchors.anchor.EvictedAnchorInfo;
import dev.dunnam.diceanchors.anchor.event.SupersessionReason;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.common.core.types.SimilarityResult;
import com.embabel.common.core.types.SimpleSimilaritySearchResult;
import com.embabel.common.core.types.TextSimilaritySearchRequest;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionQuery;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.PropositionStatus;
import jakarta.annotation.PostConstruct;
import org.drivine.manager.CascadeType;
import org.drivine.manager.GraphObjectManager;
import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Drivine-backed repository for propositions that also supports anchor promotion,
 * reinforcement, eviction, and authority upgrades.
 * <p>
 * Implements PropositionRepository so it can serve as the primary store for DICE
 * extraction while extending the contract with anchor-lifecycle methods.
 * <p>
 * Invariant A1: rank is always clamped to [100, 900] on write when non-zero.
 * Invariant A2: pinned anchors are never evicted by {@link #evictLowestRanked}.
 * Authority transitions (both promotion and demotion) are applied via setAuthority(). Business rules live in AnchorEngine.
 */
@Service
public class AnchorRepository implements PropositionRepository {

    private static final Logger logger = LoggerFactory.getLogger(AnchorRepository.class);
    private static final String PROPOSITION_VECTOR_INDEX = "proposition_embedding_index";

    private final GraphObjectManager graphObjectManager;
    private final PersistenceManager persistenceManager;
    private final EmbeddingService embeddingService;
    private final dev.dunnam.diceanchors.DiceAnchorsProperties properties;

    public AnchorRepository(
            GraphObjectManager graphObjectManager,
            PersistenceManager persistenceManager,
            EmbeddingService embeddingService,
            dev.dunnam.diceanchors.DiceAnchorsProperties properties) {
        this.graphObjectManager = graphObjectManager;
        this.persistenceManager = persistenceManager;
        this.embeddingService = embeddingService;
        this.properties = properties;
    }

    @PostConstruct
    public void provision() {
        var persistenceConfig = properties.persistence();
        if (persistenceConfig != null && persistenceConfig.clearOnStart()) {
            logger.info("Clearing proposition store on startup");
            clearAll();
        }
        normalizeLegacyAnchorStatuses();
        migrateMemoryTiers(
                properties.anchor().tier() != null ? properties.anchor().tier().hotThreshold() : 600,
                properties.anchor().tier() != null ? properties.anchor().tier().warmThreshold() : 350);
        logger.info("Provisioning proposition vector index");
        createVectorIndex(PROPOSITION_VECTOR_INDEX, "Proposition");
    }

    private void createVectorIndex(String name, String label) {
        var statement = """
                CREATE VECTOR INDEX `%s` IF NOT EXISTS
                FOR (n:%s) ON (n.embedding)
                OPTIONS {indexConfig: {
                    `vector.dimensions`: %d,
                    `vector.similarity_function`: 'cosine'
                }}
                """.formatted(name, label, embeddingService.getDimensions());
        try {
            persistenceManager.execute(QuerySpecification.withStatement(statement));
            logger.info("Created vector index {} on {}", name, label);
        } catch (Exception e) {
            logger.warn("Could not create vector index {}: {}", name, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // PropositionRepository implementation (from DrivinePropositionRepository)
    // -------------------------------------------------------------------------

    @Override
    public @NonNull String getLuceneSyntaxNotes() {
        return "fully supported";
    }

    @Override
    @Transactional
    public @NonNull Proposition save(@NonNull Proposition proposition) {
        var view = PropositionView.fromDice(proposition);
        graphObjectManager.save(view, CascadeType.NONE);

        var embedding = embeddingService.embed(proposition.getText());
        var cypher = """
                MATCH (p:Proposition {id: $id})
                SET p.embedding = $embedding
                RETURN count(p) AS updated
                """;
        var params = Map.of(
                "id", proposition.getId(),
                "embedding", embedding
        );
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Set embedding for proposition {}", proposition.getId());
        } catch (Exception e) {
            logger.warn("Failed to set embedding for proposition {}: {}", proposition.getId(), e.getMessage());
        }
        return proposition;
    }

    /**
     * Save a PropositionNode directly (used for manual anchor injection).
     */
    @Transactional
    public @NonNull PropositionNode saveNode(@NonNull PropositionNode node) {
        graphObjectManager.save(new PropositionView(node, List.of()), CascadeType.NONE);

        var embedding = embeddingService.embed(node.getText());
        var cypher = """
                MATCH (p:Proposition {id: $id})
                SET p.embedding = $embedding
                RETURN count(p) AS updated
                """;
        var params = Map.of(
                "id", node.getId(),
                "embedding", embedding
        );
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Set embedding for proposition {}", node.getId());
        } catch (Exception e) {
            logger.warn("Failed to set embedding for proposition {}: {}", node.getId(), e.getMessage());
        }
        return node;
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<Proposition> findByMinLevel(int minLevel) {
        var whereClause = "proposition.level >= " + minLevel;
        return graphObjectManager.loadAll(PropositionView.class, whereClause).stream()
                                 .map(PropositionView::toDice)
                                 .toList();
    }

    /**
     * Find propositions with minimum level for a specific context.
     */
    @Transactional(readOnly = true)
    public @NonNull List<Proposition> findByMinLevelAndContext(int minLevel, @NonNull String contextId) {
        var cypher = """
                MATCH (p:Proposition)
                WHERE p.level >= $minLevel AND p.contextId = $contextId
                RETURN p.id AS id
                """;
        var params = Map.of("minLevel", minLevel, "contextId", contextId);
        try {
            var ids = persistenceManager.query(
                    QuerySpecification.withStatement(cypher).bind(params).transform(String.class));
            return ids.stream()
                      .map(this::findById)
                      .filter(p -> p != null)
                      .toList();
        } catch (Exception e) {
            logger.warn("findByMinLevelAndContext query failed for context {}: {}", contextId, e.getMessage());
            return List.of();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public @Nullable Proposition findById(@NonNull String id) {
        var view = graphObjectManager.load(id, PropositionView.class);
        return view != null ? view.toDice() : null;
    }

    /**
     * Load a proposition as its internal {@link PropositionNode} representation,
     * including anchor fields (rank, authority, pinned, reinforcementCount).
     * Prefer this over {@link #findById} when anchor-specific fields are needed.
     *
     * @param id the proposition node ID
     * @return an Optional containing the PropositionNode, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<PropositionNode> findPropositionNodeById(@NonNull String id) {
        var view = graphObjectManager.load(id, PropositionView.class);
        return Optional.ofNullable(view != null ? view.getProposition() : null);
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<Proposition> findAll() {
        return graphObjectManager.loadAll(PropositionView.class).stream()
                                 .map(PropositionView::toDice)
                                 .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<Proposition> findByEntity(@NonNull RetrievableIdentifier identifier) {
        var cypher = """
                MATCH (p:Proposition)-[:HAS_MENTION]->(m:Mention)
                WHERE m.resolvedId = $resolvedId
                  AND (toLower(m.type) = toLower($type)
                       OR (toLower($type) = 'user' AND toLower(m.type) CONTAINS 'user'))
                RETURN DISTINCT p.id AS id
                """;
        var params = Map.of(
                "resolvedId", identifier.getId(),
                "type", identifier.getType()
        );

        try {
            var ids = persistenceManager.query(
                    QuerySpecification
                            .withStatement(cypher)
                            .bind(params)
                            .transform(String.class)
            );
            return ids.stream()
                      .map(this::findById)
                      .filter(p -> p != null)
                      .toList();
        } catch (Exception e) {
            logger.warn("findByEntity query failed: {}, falling back to in-memory", e.getMessage());
            return findAll().stream().filter(p ->
                                                     p.getMentions().stream().anyMatch(m ->
                                                                                               isTypeCompatible(m.getType(), identifier.getType()) &&
                                                                                               identifier.getId().equals(m.getResolvedId())
                                                     )
            ).toList();
        }
    }

    private boolean isTypeCompatible(String mentionType, String identifierType) {
        if (mentionType.equalsIgnoreCase(identifierType)) {
            return true;
        }
        if ("User".equalsIgnoreCase(identifierType)) {
            return mentionType.toLowerCase().contains("user");
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<SimilarityResult<Proposition>> findSimilarWithScores(
            @NonNull TextSimilaritySearchRequest request) {
        var embedding = embeddingService.embed(request.getQuery());
        var cypher = """
                CALL db.index.vector.queryNodes($vectorIndex, $topK, $queryVector)
                YIELD node AS p, score
                WHERE score >= $similarityThreshold
                RETURN {
                    id: p.id,
                    score: score
                } AS result
                ORDER BY score DESC
                """;

        var params = Map.of(
                "vectorIndex", PROPOSITION_VECTOR_INDEX,
                "topK", request.getTopK(),
                "queryVector", embedding,
                "similarityThreshold", request.getSimilarityThreshold()
        );

        logger.debug("Executing proposition vector search with query: {}", request.getQuery());

        try {
            var rows = persistenceManager.query(
                    QuerySpecification
                            .withStatement(cypher)
                            .bind(params)
                            .mapWith(new PropositionSimilarityMapper())
            );

            logger.debug("Vector search returned {} rows", rows.size());

            return rows.stream()
                       .<SimilarityResult<Proposition>> map(row -> {
                           var proposition = findById(row.id());
                           return proposition != null
                                   ? new SimpleSimilaritySearchResult<>(proposition, row.score())
                                   : null;
                       })
                       .filter(r -> r != null)
                       .toList();
        } catch (Exception e) {
            logger.error("Vector search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Result of building a Cypher query from a PropositionQuery.
     */
    public record CypherQuery(String cypher, Map<String, Object> params) {}

    /**
     * Build a Cypher query from a PropositionQuery.
     * Exposed for testing.
     */
    public CypherQuery buildCypher(@NonNull PropositionQuery query) {
        var whereConditions = new java.util.ArrayList<String>();
        var params = new java.util.HashMap<String, Object>();

        if (query.getContextIdValue() != null) {
            whereConditions.add("p.contextId = $contextId");
            params.put("contextId", query.getContextIdValue());
        }
        if (query.getStatus() != null) {
            whereConditions.add("p.status = $status");
            params.put("status", query.getStatus().name());
        }
        if (query.getMinLevel() != null) {
            whereConditions.add("p.level >= $minLevel");
            params.put("minLevel", query.getMinLevel());
        }
        if (query.getMaxLevel() != null) {
            whereConditions.add("p.level <= $maxLevel");
            params.put("maxLevel", query.getMaxLevel());
        }
        if (query.getCreatedAfter() != null) {
            whereConditions.add("p.createdAt >= $createdAfter");
            params.put("createdAfter", query.getCreatedAfter().toEpochMilli());
        }
        if (query.getCreatedBefore() != null) {
            whereConditions.add("p.createdAt <= $createdBefore");
            params.put("createdBefore", query.getCreatedBefore().toEpochMilli());
        }
        if (query.getRevisedAfter() != null) {
            whereConditions.add("p.revisedAt >= $revisedAfter");
            params.put("revisedAfter", query.getRevisedAfter().toEpochMilli());
        }
        if (query.getRevisedBefore() != null) {
            whereConditions.add("p.revisedAt <= $revisedBefore");
            params.put("revisedBefore", query.getRevisedBefore().toEpochMilli());
        }
        if (query.getMinEffectiveConfidence() != null) {
            var asOf = query.getEffectiveConfidenceAsOf() != null
                    ? query.getEffectiveConfidenceAsOf()
                    : java.time.Instant.now();
            params.put("minEffectiveConfidence", query.getMinEffectiveConfidence());
            params.put("asOfMillis", asOf.toEpochMilli());
            params.put("decayK", query.getDecayK());
        }

        String cypher;
        if (query.getEntityId() != null) {
            params.put("entityId", query.getEntityId());
            var additionalConditions = whereConditions.isEmpty() ? "" : " AND " + String.join(" AND ", whereConditions);
            cypher = """
                    MATCH (p:Proposition)-[:HAS_MENTION]->(m:Mention)
                    WHERE m.resolvedId = $entityId%s
                    RETURN DISTINCT p.id AS id
                    """.formatted(additionalConditions);
        } else {
            var whereClause = whereConditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", whereConditions);
            cypher = """
                    MATCH (p:Proposition)
                    %s
                    RETURN p.id AS id
                    """.formatted(whereClause);
        }

        cypher = switch (query.getOrderBy()) {
            case EFFECTIVE_CONFIDENCE_DESC -> cypher.replace("RETURN", "ORDER BY p.confidence DESC RETURN");
            case CREATED_DESC -> cypher.replace("RETURN", "ORDER BY p.createdAt DESC RETURN");
            case REVISED_DESC -> cypher.replace("RETURN", "ORDER BY p.revisedAt DESC RETURN");
            case NONE -> cypher;
            default -> cypher;
        };

        if (query.getLimit() != null) {
            cypher += " LIMIT " + query.getLimit();
        }

        return new CypherQuery(cypher, params);
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<Proposition> query(@NonNull PropositionQuery query) {
        var cypherQuery = buildCypher(query);
        logger.debug("Executing proposition query: {}", cypherQuery.cypher());

        try {
            var ids = persistenceManager.query(
                    QuerySpecification
                            .withStatement(cypherQuery.cypher())
                            .bind(cypherQuery.params())
                            .transform(String.class)
            );

            var results = ids.stream()
                             .map(this::findById)
                             .filter(p -> p != null)
                             .toList();

            if (query.getMinEffectiveConfidence() != null) {
                var asOf = query.getEffectiveConfidenceAsOf() != null
                        ? query.getEffectiveConfidenceAsOf()
                        : java.time.Instant.now();
                var k = query.getDecayK();
                var threshold = query.getMinEffectiveConfidence();

                results = results.stream()
                                 .filter(p -> {
                                     var daysSinceRevision = java.time.Duration.between(
                                             p.getRevised() != null ? p.getRevised() : p.getCreated(),
                                             asOf
                                     ).toDays();
                                     var effectiveConfidence = p.getConfidence() * Math.exp(-k * daysSinceRevision / 365.0);
                                     return effectiveConfidence >= threshold;
                                 })
                                 .toList();
            }

            return results;
        } catch (Exception e) {
            logger.error("Proposition query failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<SimilarityResult<Proposition>> findSimilarWithScores(
            @NonNull TextSimilaritySearchRequest request,
            @NonNull PropositionQuery query) {
        var embedding = embeddingService.embed(request.getQuery());

        var whereConditions = new java.util.ArrayList<String>();
        whereConditions.add("score >= $similarityThreshold");

        var params = new java.util.HashMap<String, Object>();
        params.put("vectorIndex", PROPOSITION_VECTOR_INDEX);
        params.put("topK", request.getTopK());
        params.put("queryVector", embedding);
        params.put("similarityThreshold", request.getSimilarityThreshold());

        if (query.getContextIdValue() != null) {
            whereConditions.add("p.contextId = $contextId");
            params.put("contextId", query.getContextIdValue());
        }
        if (query.getStatus() != null) {
            whereConditions.add("p.status = $status");
            params.put("status", query.getStatus().name());
        }
        if (query.getMinLevel() != null) {
            whereConditions.add("p.level >= $minLevel");
            params.put("minLevel", query.getMinLevel());
        }
        if (query.getMaxLevel() != null) {
            whereConditions.add("p.level <= $maxLevel");
            params.put("maxLevel", query.getMaxLevel());
        }

        var whereClause = String.join(" AND ", whereConditions);
        var cypher = """
                CALL db.index.vector.queryNodes($vectorIndex, $topK, $queryVector)
                YIELD node AS p, score
                WHERE %s
                RETURN {
                    id: p.id,
                    score: score
                } AS result
                ORDER BY score DESC
                """.formatted(whereClause);

        logger.debug("Executing filtered proposition vector search with query: {}, contextId: {}",
                     request.getQuery(), query.getContextIdValue());

        try {
            var rows = persistenceManager.query(
                    QuerySpecification
                            .withStatement(cypher)
                            .bind(params)
                            .mapWith(new PropositionSimilarityMapper())
            );

            logger.debug("Filtered vector search returned {} rows", rows.size());

            return rows.stream()
                       .<SimilarityResult<Proposition>> map(row -> {
                           var proposition = findById(row.id());
                           return proposition != null
                                   ? new SimpleSimilaritySearchResult<>(proposition, row.score())
                                   : null;
                       })
                       .filter(r -> r != null)
                       .toList();
        } catch (Exception e) {
            logger.error("Filtered vector search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<Proposition> findByStatus(@NonNull PropositionStatus status) {
        var whereClause = "proposition.status = '" + status.name() + "'";
        return graphObjectManager.loadAll(PropositionView.class, whereClause).stream()
                                 .map(PropositionView::toDice)
                                 .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<Proposition> findByGrounding(@NonNull String chunkId) {
        var cypher = """
                MATCH (p:Proposition)
                WHERE $chunkId IN p.grounding
                RETURN p.id AS id
                """;
        var params = Map.of("chunkId", chunkId);

        try {
            var ids = persistenceManager.query(
                    QuerySpecification
                            .withStatement(cypher)
                            .bind(params)
                            .transform(String.class)
            );
            return ids.stream()
                      .map(this::findById)
                      .filter(p -> p != null)
                      .toList();
        } catch (Exception e) {
            logger.warn("findByGrounding query failed: {}, falling back to in-memory", e.getMessage());
            return findAll().stream()
                            .filter(p -> p.getGrounding().contains(chunkId))
                            .toList();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<Proposition> findByContextIdValue(@NonNull String contextIdValue) {
        var cypher = """
                MATCH (p:Proposition)
                WHERE p.contextId = $contextId
                RETURN p.id AS id
                """;
        var params = Map.of("contextId", contextIdValue);
        try {
            var ids = persistenceManager.query(
                    QuerySpecification.withStatement(cypher).bind(params).transform(String.class));
            return ids.stream()
                      .map(this::findById)
                      .filter(p -> p != null)
                      .toList();
        } catch (Exception e) {
            logger.warn("findByContextIdValue query failed for context {}: {}", contextIdValue, e.getMessage());
            return List.of();
        }
    }

    /**
     * Force a contextId value onto existing propositions by ID.
     * Useful when upstream extraction omits context propagation.
     *
     * @param propositionIds proposition IDs to update
     * @param contextId context ID to assign
     *
     * @return number of rows updated
     */
    @Transactional
    public int assignContextIds(@NonNull List<String> propositionIds, @NonNull String contextId) {
        var ids = propositionIds.stream()
                                .filter(id -> id != null && !id.isBlank())
                                .distinct()
                                .toList();
        if (ids.isEmpty()) {
            return 0;
        }

        var cypher = """
                MATCH (p:Proposition)
                WHERE p.id IN $ids
                SET p.contextId = $contextId,
                    p.status = CASE
                        WHEN p.status IS NULL OR trim(toString(p.status)) = '' THEN 'ACTIVE'
                        ELSE p.status
                    END
                RETURN count(p) AS updated
                """;
        var params = Map.of(
                "ids", ids,
                "contextId", contextId);
        try {
            var spec = QuerySpecification.withStatement(cypher).bind(params).transform(Long.class);
            var updated = persistenceManager.getOne(spec);
            return updated != null ? updated.intValue() : 0;
        } catch (Exception e) {
            logger.warn("Failed to assign context {} to {} propositions: {}", contextId, ids.size(), e.getMessage());
            return 0;
        }
    }

    /**
     * Tag propositions with a source ID for lineage tracking.
     * Appends the source to the existing sourceIds list if not already present.
     *
     * @param propositionIds proposition IDs to tag
     * @param sourceId       source identifier (e.g., "dm", "player", "system")
     * @return number of rows updated
     */
    @Transactional
    public int tagSourceIds(@NonNull List<String> propositionIds, @NonNull String sourceId) {
        var ids = propositionIds.stream()
                                .filter(id -> id != null && !id.isBlank())
                                .distinct()
                                .toList();
        if (ids.isEmpty()) {
            return 0;
        }

        var cypher = """
                MATCH (p:Proposition)
                WHERE p.id IN $ids
                SET p.sourceIds = CASE
                    WHEN p.sourceIds IS NULL THEN [$sourceId]
                    WHEN NOT $sourceId IN p.sourceIds THEN p.sourceIds + $sourceId
                    ELSE p.sourceIds
                END
                RETURN count(p) AS updated
                """;
        var params = Map.of(
                "ids", ids,
                "sourceId", sourceId);
        try {
            var spec = QuerySpecification.withStatement(cypher).bind(params).transform(Long.class);
            var updated = persistenceManager.getOne(spec);
            return updated != null ? updated.intValue() : 0;
        } catch (Exception e) {
            logger.warn("Failed to tag source {} on {} propositions: {}", sourceId, ids.size(), e.getMessage());
            return 0;
        }
    }

    /**
     * Find active, unanchored propositions for prompt assembly.
     * Unanchored propositions are those with rank 0.
     *
     * @param contextId the context ID
     * @param limit maximum propositions to return; values <= 0 return empty
     *
     * @return propositions ordered by confidence/revision recency
     */
    @Transactional(readOnly = true)
    public @NonNull List<PropositionNode> findActiveUnanchoredPropositions(@NonNull String contextId, int limit) {
        var effectiveLimit = Math.max(0, limit);
        if (effectiveLimit == 0) {
            return List.of();
        }

        var cypher = """
                MATCH (p:Proposition {contextId: $contextId})
                WHERE coalesce(p.rank, 0) = 0
                  AND coalesce(p.status, 'ACTIVE') IN ['ACTIVE', 'PROMOTED']
                RETURN p.id AS id
                ORDER BY p.confidence DESC,
                         coalesce(p.revised, p.created) DESC
                LIMIT $limit
                """;
        var params = Map.of(
                "contextId", contextId,
                "limit", effectiveLimit);
        try {
            var ids = persistenceManager.query(
                    QuerySpecification.withStatement(cypher).bind(params).transform(String.class)
            );
            return ids.stream()
                      .map(id -> graphObjectManager.load(id, PropositionView.class))
                      .filter(view -> view != null)
                      .map(PropositionView::getProposition)
                      .toList();
        } catch (Exception e) {
            logger.warn("findActiveUnanchoredPropositions query failed for context {}: {}", contextId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Query context-scoped entity nodes derived from mention data.
     */
    @Transactional(readOnly = true)
    public @NonNull List<EntityMentionNode> findEntityMentionNodes(
            @NonNull String contextId,
            @Nullable String entityType,
            boolean activeOnly) {
        if (contextId.isBlank()) {
            return List.of();
        }

        var cypher = """
                MATCH (p:Proposition {contextId: $contextId})-[:HAS_MENTION]->(m:Mention)
                WHERE ($activeOnly = false OR coalesce(p.status, 'ACTIVE') IN ['ACTIVE', 'PROMOTED'])
                WITH p,
                     CASE
                       WHEN m.resolvedId IS NOT NULL AND trim(m.resolvedId) <> '' THEN trim(m.resolvedId)
                       ELSE $unresolvedPrefix + toLower(trim(coalesce(m.type, 'unknown'))) + ':' + toLower(trim(coalesce(m.span, 'unknown')))
                     END AS entityId,
                     coalesce(nullif(trim(m.span), ''), trim(m.resolvedId), '[unknown]') AS label,
                     coalesce(nullif(trim(m.type), ''), 'unknown') AS entityType
                WHERE entityId <> ''
                  AND ($entityType IS NULL OR toLower(entityType) = toLower($entityType))
                WITH entityId, label, entityType,
                     count(*) AS mentionCount,
                     count(DISTINCT p.id) AS propositionCount
                ORDER BY propositionCount DESC, mentionCount DESC, entityId ASC
                RETURN {entityId: entityId, label: label, entityType: entityType,
                        mentionCount: mentionCount, propositionCount: propositionCount} AS result
                """;
        var params = new HashMap<String, Object>();
        params.put("contextId", contextId);
        params.put("entityType", normalizeEntityTypeFilter(entityType));
        params.put("activeOnly", activeOnly);
        params.put("unresolvedPrefix", "unresolved:");
        try {
            var rows = persistenceManager.query(
                    QuerySpecification.withStatement(cypher).bind(params).transform(Map.class)
            );
            return rows.stream()
                       .map(this::toEntityMentionNode)
                       .filter(node -> node != null)
                       .toList();
        } catch (Exception e) {
            logger.warn("findEntityMentionNodes query failed for context {}: {}", contextId, e.getMessage());
            return List.of();
        }
    }

    private void normalizeLegacyAnchorStatuses() {
        var cypher = """
                MATCH (p:Proposition)
                WHERE p.status = 'ARCHIVED'
                SET p.status = 'SUPERSEDED'
                RETURN count(p) AS normalized
                """;
        try {
            var spec = QuerySpecification.withStatement(cypher).transform(Long.class);
            var normalized = persistenceManager.getOne(spec);
            if (normalized != null && normalized > 0) {
                logger.info("Normalized {} legacy ARCHIVED proposition statuses to SUPERSEDED", normalized);
            }
        } catch (Exception e) {
            logger.warn("Failed to normalize legacy ARCHIVED statuses: {}", e.getMessage());
        }
    }

    /**
     * Query weighted co-mention edges derived from proposition mentions.
     */
    @Transactional(readOnly = true)
    public @NonNull List<EntityMentionEdge> findEntityMentionEdges(
            @NonNull String contextId,
            int minEdgeWeight,
            @Nullable String entityType,
            boolean activeOnly) {
        if (contextId.isBlank()) {
            return List.of();
        }
        var clampedMinWeight = Math.max(1, minEdgeWeight);
        var cypher = """
                MATCH (p:Proposition {contextId: $contextId})-[:HAS_MENTION]->(m1:Mention),
                      (p)-[:HAS_MENTION]->(m2:Mention)
                WHERE id(m1) < id(m2)
                  AND ($activeOnly = false OR coalesce(p.status, 'ACTIVE') IN ['ACTIVE', 'PROMOTED'])
                WITH p,
                     CASE
                       WHEN m1.resolvedId IS NOT NULL AND trim(m1.resolvedId) <> '' THEN trim(m1.resolvedId)
                       ELSE $unresolvedPrefix + toLower(trim(coalesce(m1.type, 'unknown'))) + ':' + toLower(trim(coalesce(m1.span, 'unknown')))
                     END AS leftEntityId,
                     coalesce(nullif(trim(m1.type), ''), 'unknown') AS leftType,
                     CASE
                       WHEN m2.resolvedId IS NOT NULL AND trim(m2.resolvedId) <> '' THEN trim(m2.resolvedId)
                       ELSE $unresolvedPrefix + toLower(trim(coalesce(m2.type, 'unknown'))) + ':' + toLower(trim(coalesce(m2.span, 'unknown')))
                     END AS rightEntityId,
                     coalesce(nullif(trim(m2.type), ''), 'unknown') AS rightType
                WHERE leftEntityId <> rightEntityId
                  AND ($entityType IS NULL
                       OR toLower(leftType) = toLower($entityType)
                       OR toLower(rightType) = toLower($entityType))
                WITH p,
                     CASE WHEN leftEntityId < rightEntityId THEN leftEntityId ELSE rightEntityId END AS sourceEntityId,
                     CASE WHEN leftEntityId < rightEntityId THEN rightEntityId ELSE leftEntityId END AS targetEntityId
                WITH sourceEntityId, targetEntityId, collect(DISTINCT p.id) AS propositionIds
                WITH sourceEntityId, targetEntityId, propositionIds, size(propositionIds) AS weight
                WHERE weight >= $minEdgeWeight
                ORDER BY weight DESC, sourceEntityId ASC, targetEntityId ASC
                RETURN {sourceEntityId: sourceEntityId, targetEntityId: targetEntityId,
                        weight: weight, propositionIds: propositionIds} AS result
                """;
        var params = new HashMap<String, Object>();
        params.put("contextId", contextId);
        params.put("entityType", normalizeEntityTypeFilter(entityType));
        params.put("activeOnly", activeOnly);
        params.put("minEdgeWeight", clampedMinWeight);
        params.put("unresolvedPrefix", "unresolved:");
        try {
            var rows = persistenceManager.query(
                    QuerySpecification.withStatement(cypher).bind(params).transform(Map.class)
            );
            return rows.stream()
                       .map(this::toEntityMentionEdge)
                       .filter(edge -> edge != null)
                       .toList();
        } catch (Exception e) {
            logger.warn("findEntityMentionEdges query failed for context {}: {}", contextId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Query a context-scoped entity mention graph using supplied filters.
     */
    @Transactional(readOnly = true)
    public @NonNull EntityMentionGraph findEntityMentionGraph(
            @NonNull String contextId,
            @Nullable EntityMentionGraphFilter filter) {
        if (contextId.isBlank()) {
            return EntityMentionGraph.empty();
        }

        var effectiveFilter = filter != null ? filter : EntityMentionGraphFilter.defaults();
        var nodes = findEntityMentionNodes(contextId, effectiveFilter.entityType(), effectiveFilter.activeOnly());
        var edges = findEntityMentionEdges(contextId, effectiveFilter.minEdgeWeight(),
                                           effectiveFilter.entityType(), effectiveFilter.activeOnly());
        if (nodes.isEmpty() || edges.isEmpty()) {
            return new EntityMentionGraph(nodes, edges);
        }

        var connectedNodeIds = new HashSet<String>();
        edges.forEach(edge -> {
            connectedNodeIds.add(edge.sourceEntityId());
            connectedNodeIds.add(edge.targetEntityId());
        });
        var filteredNodes = nodes.stream()
                                 .filter(node -> connectedNodeIds.contains(node.entityId()))
                                 .toList();
        return new EntityMentionGraph(filteredNodes, edges);
    }

    private @Nullable EntityMentionNode toEntityMentionNode(@Nullable Map<String, ?> row) {
        if (row == null) {
            return null;
        }
        var label = asString(row.get("label"));
        if (label == null || label.isBlank()) {
            return null;
        }
        var entityType = asStringOrDefault(row.get("entityType"), "unknown");
        var entityId = asString(row.get("entityId"));
        if (entityId == null || entityId.isBlank()) {
            entityId = EntityMentionIdentity.fallbackKey(label, entityType);
        }
        return new EntityMentionNode(
                entityId,
                label,
                entityType,
                asInt(row.get("mentionCount")),
                asInt(row.get("propositionCount"))
        );
    }

    private @Nullable EntityMentionEdge toEntityMentionEdge(@Nullable Map<String, ?> row) {
        if (row == null) {
            return null;
        }
        var sourceEntityId = asString(row.get("sourceEntityId"));
        var targetEntityId = asString(row.get("targetEntityId"));
        if (sourceEntityId == null || sourceEntityId.isBlank()
            || targetEntityId == null || targetEntityId.isBlank()
            || sourceEntityId.equals(targetEntityId)) {
            return null;
        }
        var propositionIds = asStringList(row.get("propositionIds"));
        var distinctIds = propositionIds.stream().filter(id -> !id.isBlank()).distinct().toList();
        var weight = asInt(row.get("weight"));
        if (weight <= 0) {
            weight = distinctIds.size();
        }
        if (weight <= 0) {
            return null;
        }
        return new EntityMentionEdge(sourceEntityId, targetEntityId, weight, distinctIds);
    }

    private @Nullable String normalizeEntityTypeFilter(@Nullable String entityType) {
        if (entityType == null || entityType.isBlank() || "ALL".equalsIgnoreCase(entityType)) {
            return null;
        }
        return entityType.trim();
    }

    private @Nullable String asString(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private String asStringOrDefault(@Nullable Object value, String defaultValue) {
        var text = asString(value);
        return text == null || text.isBlank() ? defaultValue : text;
    }

    private int asInt(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private List<String> asStringList(@Nullable Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        var results = new ArrayList<String>();
        for (var item : rawList) {
            var text = asString(item);
            if (text != null) {
                results.add(text);
            }
        }
        return results;
    }

    @Override
    @Transactional
    public boolean delete(@NonNull String id) {
        int deleted = graphObjectManager.delete(id, PropositionView.class);
        return deleted > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public int count() {
        var spec = QuerySpecification
                .withStatement("MATCH (p:Proposition) RETURN count(p) AS count")
                .transform(Long.class);
        Long result = persistenceManager.getOne(spec);
        return result.intValue();
    }

    /**
     * Delete all propositions from the database.
     *
     * @return the number of propositions deleted
     */
    @Transactional
    public int clearAll() {
        var countSpec = QuerySpecification
                .withStatement("MATCH (p:Proposition) RETURN count(p) AS count")
                .transform(Long.class);
        Long count = persistenceManager.getOne(countSpec);

        var deleteSpec = QuerySpecification
                .withStatement("MATCH (p:Proposition) DETACH DELETE p");
        persistenceManager.execute(deleteSpec);

        logger.info("Deleted {} propositions", count);
        return count.intValue();
    }

    /**
     * Delete all propositions for a specific context.
     *
     * @param contextId the context ID to clear propositions for
     *
     * @return the number of propositions deleted
     */
    @Transactional
    public int clearByContext(@NonNull String contextId) {
        var countSpec = QuerySpecification
                .withStatement("MATCH (p:Proposition {contextId: $contextId}) RETURN count(p) AS count")
                .bind(Map.of("contextId", contextId))
                .transform(Long.class);
        Long count = persistenceManager.getOne(countSpec);

        var deleteSpec = QuerySpecification
                .withStatement("MATCH (p:Proposition {contextId: $contextId}) DETACH DELETE p")
                .bind(Map.of("contextId", contextId));
        persistenceManager.execute(deleteSpec);

        logger.info("Deleted {} propositions for context {}", count, contextId);
        return count.intValue();
    }

    // -------------------------------------------------------------------------
    // Anchor-specific methods
    // -------------------------------------------------------------------------

    /**
     * Find active anchors for the given context, ordered by rank descending.
     * Only returns propositions that have been promoted (rank > 0) and are ACTIVE.
     *
     * @param contextId the conversation or session context
     *
     * @return list of active anchor nodes ordered highest-rank first
     */
    @Transactional(readOnly = true)
    public List<PropositionNode> findActiveAnchors(@NonNull String contextId) {
        var cypher = """
                MATCH (p:Proposition {contextId: $contextId})
                WHERE p.rank > 0 AND coalesce(p.status, 'ACTIVE') IN ['ACTIVE', 'PROMOTED']
                RETURN p.id AS id
                ORDER BY p.rank DESC
                """;
        var params = Map.of("contextId", contextId);

        try {
            var ids = persistenceManager.query(
                    QuerySpecification
                            .withStatement(cypher)
                            .bind(params)
                            .transform(String.class)
            );
            return ids.stream()
                      .map(id -> graphObjectManager.load(id, PropositionView.class))
                      .filter(view -> view != null)
                      .map(PropositionView::getProposition)
                      .toList();
        } catch (Exception e) {
            logger.warn("findActiveAnchors query failed for context {}: {}", contextId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Find all anchors (active and archived) for the given context.
     * Anchors are identified by non-null authority or rank > 0.
     */
    @Transactional(readOnly = true)
    public List<PropositionNode> findAnchorsByContext(@NonNull String contextId) {
        var cypher = """
                MATCH (p:Proposition {contextId: $contextId})
                WHERE p.rank > 0 OR p.authority IS NOT NULL
                RETURN p.id AS id
                ORDER BY CASE p.status WHEN 'ACTIVE' THEN 0 WHEN 'PROMOTED' THEN 0 ELSE 1 END,
                         p.rank DESC,
                         p.created DESC
                """;
        var params = Map.of("contextId", contextId);

        try {
            var ids = persistenceManager.query(
                    QuerySpecification
                            .withStatement(cypher)
                            .bind(params)
                            .transform(String.class)
            );
            return ids.stream()
                      .map(id -> graphObjectManager.load(id, PropositionView.class))
                      .filter(view -> view != null)
                      .map(PropositionView::getProposition)
                      .toList();
        } catch (Exception e) {
            logger.warn("findAnchorsByContext query failed for context {}: {}", contextId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Find pinned anchors for the given context, ordered by rank descending.
     * Pinned anchors are immune to eviction.
     *
     * @param contextId the conversation or session context
     *
     * @return list of pinned anchor nodes ordered highest-rank first
     */
    @Transactional(readOnly = true)
    public List<PropositionNode> findPinnedAnchors(@NonNull String contextId) {
        var cypher = """
                MATCH (p:Proposition {contextId: $contextId})
                WHERE p.pinned = true
                RETURN p.id AS id
                ORDER BY p.rank DESC
                """;
        var params = Map.of("contextId", contextId);

        try {
            var ids = persistenceManager.query(
                    QuerySpecification
                            .withStatement(cypher)
                            .bind(params)
                            .transform(String.class)
            );
            return ids.stream()
                      .map(id -> graphObjectManager.load(id, PropositionView.class))
                      .filter(view -> view != null)
                      .map(PropositionView::getProposition)
                      .toList();
        } catch (Exception e) {
            logger.warn("findPinnedAnchors query failed for context {}: {}", contextId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Promote a proposition to anchor status by setting its rank and authority.
     * Rank is clamped to [100, 900]. Sets lastReinforced to now.
     *
     * @param propositionId the ID of the proposition to promote
     * @param rank          desired rank, clamped to [100, 900]
     * @param authority     authority level: PROVISIONAL, UNRELIABLE, RELIABLE, or CANON
     */
    @Transactional
    public void promoteToAnchor(@NonNull String propositionId, int rank, @NonNull String authority) {
        promoteToAnchor(propositionId, rank, authority, null, null);
    }

    @Transactional
    public void promoteToAnchor(@NonNull String propositionId, int rank, @NonNull String authority,
                                @Nullable String memoryTier) {
        promoteToAnchor(propositionId, rank, authority, memoryTier, null);
    }

    @Transactional
    public void promoteToAnchor(@NonNull String propositionId, int rank, @NonNull String authority,
                                @Nullable String memoryTier, @Nullable String authorityCeiling) {
        int clamped = Math.max(100, Math.min(900, rank));
        var cypher = """
                MATCH (p:Proposition {id: $id})
                SET p.rank = $rank,
                    p.authority = $authority,
                    p.status = 'ACTIVE',
                    p.lastReinforced = $now,
                    p.memoryTier = $memoryTier,
                    p.authorityCeiling = $authorityCeiling,
                    p.validFrom = $now,
                    p.transactionStart = $now
                """;
        var params = new HashMap<String, Object>();
        params.put("id", propositionId);
        params.put("rank", clamped);
        params.put("authority", authority);
        params.put("now", Instant.now().toString());
        params.put("memoryTier", memoryTier);
        params.put("authorityCeiling", authorityCeiling);
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Promoted proposition {} to anchor with rank={} authority={} tier={} ceiling={}",
                    propositionId, clamped, authority, memoryTier, authorityCeiling);
        } catch (Exception e) {
            logger.error("Failed to promote proposition {} to anchor: {}", propositionId, e.getMessage(), e);
        }
    }

    /**
     * Update the rank of an anchor, clamped to [100, 900].
     * Has no effect on propositions that are not currently anchors.
     *
     * @param id      the proposition ID
     * @param newRank the desired rank, clamped to [100, 900]
     */
    @Transactional
    public void updateRank(@NonNull String id, int newRank) {
        int clamped = Math.max(100, Math.min(900, newRank));
        var cypher = """
                MATCH (p:Proposition {id: $id})
                SET p.rank = $rank
                """;
        var params = Map.of("id", id, "rank", clamped);
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Updated rank for proposition {} to {}", id, clamped);
        } catch (Exception e) {
            logger.error("Failed to update rank for proposition {}: {}", id, e.getMessage(), e);
        }
    }

    /**
     * Enable an anchor by setting status ACTIVE and ensuring a non-zero rank.
     *
     * @param id   the proposition ID
     * @param rank desired rank; clamped to [100, 900]
     */
    @Transactional
    public void activateAnchor(@NonNull String id, int rank) {
        int clamped = Math.max(100, Math.min(900, rank));
        var cypher = """
                MATCH (p:Proposition {id: $id})
                SET p.status = 'ACTIVE',
                    p.rank = $rank,
                    p.authority = coalesce(p.authority, 'PROVISIONAL')
                """;
        var params = Map.of("id", id, "rank", clamped);
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Activated anchor {} with rank {}", id, clamped);
        } catch (Exception e) {
            logger.error("Failed to activate anchor {}: {}", id, e.getMessage(), e);
        }
    }

    /**
     * Disable an anchor by setting its status to SUPERSEDED (rank preserved).
     *
     * @param id the proposition ID
     */
    @Transactional
    public void deactivateAnchor(@NonNull String id) {
        var cypher = """
                MATCH (p:Proposition {id: $id})
                SET p.status = 'SUPERSEDED'
                """;
        var params = Map.of("id", id);
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Deactivated anchor {}", id);
        } catch (Exception e) {
            logger.error("Failed to deactivate anchor {}: {}", id, e.getMessage(), e);
        }
    }

    /**
     * Reinforce an anchor: increments reinforcement count and updates lastReinforced timestamp.
     *
     * @param id the proposition ID
     */
    @Transactional
    public void reinforceAnchor(@NonNull String id) {
        var cypher = """
                MATCH (p:Proposition {id: $id})
                SET p.reinforcementCount = p.reinforcementCount + 1,
                    p.lastReinforced = $now
                """;
        var params = Map.of("id", id, "now", Instant.now().toString());
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Reinforced anchor {}", id);
        } catch (Exception e) {
            logger.error("Failed to reinforce anchor {}: {}", id, e.getMessage(), e);
        }
    }

    /**
     * Evict lowest-ranked non-pinned anchors when the active anchor count exceeds budget.
     * Evicted anchors have their status set to SUPERSEDED and rank set to 0.
     *
     * @param contextId the conversation or session context
     * @param budget    the maximum number of active anchors allowed
     *
     * @return list of evicted anchors (ID and previous rank), empty if within budget
     */
    @Transactional
    public List<EvictedAnchorInfo> evictLowestRanked(@NonNull String contextId, int budget) {
        var countCypher = """
                MATCH (p:Proposition {contextId: $contextId})
                WHERE p.rank > 0 AND coalesce(p.status, 'ACTIVE') IN ['ACTIVE', 'PROMOTED']
                RETURN count(p) AS cnt
                """;
        var countParams = Map.of("contextId", contextId);

        long current;
        try {
            var countSpec = QuerySpecification
                    .withStatement(countCypher)
                    .bind(countParams)
                    .transform(Long.class);
            Long result = persistenceManager.getOne(countSpec);
            current = result != null ? result : 0L;
        } catch (Exception e) {
            logger.error("Failed to count active anchors for eviction in context {}: {}", contextId, e.getMessage(), e);
            return List.of();
        }

        if (current <= budget) {
            return List.of();
        }

        int evictCount = (int) (current - budget);
        // Capture id and rank BEFORE setting rank to 0, then evict
        var evictCypher = """
                MATCH (p:Proposition {contextId: $contextId})
                WHERE p.rank > 0 AND coalesce(p.status, 'ACTIVE') IN ['ACTIVE', 'PROMOTED'] AND p.pinned = false
                WITH p, p.rank AS previousRank ORDER BY p.rank ASC LIMIT $evictCount
                SET p.status = 'SUPERSEDED', p.rank = 0,
                    p.validTo = toString(datetime()),
                    p.transactionEnd = toString(datetime())
                RETURN {anchorId: p.id, rank: previousRank} AS result
                """;
        var evictParams = Map.of("contextId", contextId, "evictCount", evictCount);

        try {
            var evictSpec = QuerySpecification
                    .withStatement(evictCypher)
                    .bind(evictParams)
                    .transform(Map.class);
            var rows = persistenceManager.query(evictSpec);
            var evicted = rows.stream()
                    .filter(row -> row != null)
                    .map(row -> {
                        var m = (java.util.Map<?, ?>) row;
                        var anchorId = (String) m.get("anchorId");
                        var rank = ((Number) m.get("rank")).intValue();
                        return new EvictedAnchorInfo(anchorId, rank);
                    })
                    .toList();
            logger.info("Evicted {} anchors from context {} (budget={}, was={})", evicted.size(), contextId, budget, current);
            return evicted;
        } catch (Exception e) {
            logger.error("Failed to evict anchors for context {}: {}", contextId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Count active anchors for the given context.
     * Active anchors are propositions with rank > 0 and status ACTIVE.
     *
     * @param contextId the conversation or session context
     *
     * @return the number of active anchors
     */
    @Transactional(readOnly = true)
    public int countActiveAnchors(@NonNull String contextId) {
        var cypher = """
                MATCH (p:Proposition {contextId: $contextId})
                WHERE p.rank > 0 AND coalesce(p.status, 'ACTIVE') IN ['ACTIVE', 'PROMOTED']
                RETURN count(p) AS cnt
                """;
        var params = Map.of("contextId", contextId);
        try {
            var spec = QuerySpecification
                    .withStatement(cypher)
                    .bind(params)
                    .transform(Long.class);
            Long result = persistenceManager.getOne(spec);
            return result != null ? result.intValue() : 0;
        } catch (Exception e) {
            logger.error("Failed to count active anchors for context {}: {}", contextId, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Set the authority of an anchor to any level — supports both promotion and demotion.
     * Business rules for transition validation live in {@link dev.dunnam.diceanchors.anchor.AnchorEngine},
     * not here. The repository applies the change unconditionally.
     *
     * @param id           the proposition ID
     * @param newAuthority the target authority level (PROVISIONAL, UNRELIABLE, RELIABLE, or CANON)
     */
    @Transactional
    public void setAuthority(@NonNull String id, @NonNull String newAuthority) {
        var cypher = """
                MATCH (p:Proposition {id: $id})
                SET p.authority = $newAuthority
                """;
        var params = Map.of("id", id, "newAuthority", newAuthority);
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Set authority for proposition {} to {}", id, newAuthority);
        } catch (Exception e) {
            logger.error("Failed to set authority for proposition {}: {}", id, e.getMessage(), e);
        }
    }

    /**
     * Archive an anchor by setting its status to SUPERSEDED and rank to 0.
     *
     * @param id the proposition ID
     */
    @Transactional
    public void archiveAnchor(@NonNull String id) {
        archiveAnchor(id, null);
    }

    /**
     * Archive an anchor, optionally recording the successor that replaced it.
     *
     * @param id          the proposition ID to archive
     * @param successorId the ID of the replacing anchor, or null if no successor
     */
    @Transactional
    public void archiveAnchor(@NonNull String id, @Nullable String successorId) {
        var now = Instant.now().toString();
        String cypher;
        Map<String, Object> params;
        if (successorId != null) {
            cypher = """
                    MATCH (p:Proposition {id: $id})
                    SET p.status = 'SUPERSEDED', p.rank = 0,
                        p.validTo = $now,
                        p.transactionEnd = $now,
                        p.supersededBy = $successorId
                    """;
            params = Map.of("id", id, "now", now, "successorId", successorId);
        } else {
            cypher = """
                    MATCH (p:Proposition {id: $id})
                    SET p.status = 'SUPERSEDED', p.rank = 0,
                        p.validTo = $now,
                        p.transactionEnd = $now
                    """;
            params = Map.of("id", id, "now", now);
        }
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Archived anchor {}{}", id, successorId != null ? " (superseded by " + successorId + ")" : "");
        } catch (Exception e) {
            logger.error("Failed to archive anchor {}: {}", id, e.getMessage(), e);
        }
    }

    /**
     * Semantic search for propositions within a specific context.
     * Returns scored results ordered by similarity descending.
     *
     * @param query     the search text (min 3 characters recommended)
     * @param contextId the context to scope results to
     * @param topK      maximum number of results to return
     * @param threshold minimum similarity score (0.0 to 1.0)
     *
     * @return scored proposition results ordered by similarity
     */
    @Transactional(readOnly = true)
    public List<ScoredProposition> semanticSearch(
            @NonNull String query, @NonNull String contextId, int topK, double threshold) {
        var embedding = embeddingService.embed(query);
        var cypher = """
                CALL db.index.vector.queryNodes($vectorIndex, $topK, $queryVector)
                YIELD node AS p, score
                WHERE score >= $similarityThreshold AND p.contextId = $contextId
                RETURN {
                    id: p.id,
                    score: score
                } AS result
                ORDER BY score DESC
                """;
        var params = Map.of(
                "vectorIndex", PROPOSITION_VECTOR_INDEX,
                "topK", topK,
                "queryVector", embedding,
                "similarityThreshold", threshold,
                "contextId", contextId
        );
        logger.debug("Executing scoped semantic search for context {}: {}", contextId, query);
        try {
            var rows = persistenceManager.query(
                    QuerySpecification
                            .withStatement(cypher)
                            .bind(params)
                            .mapWith(new PropositionSimilarityMapper())
            );
            return rows.stream()
                       .map(row -> {
                           var proposition = findById(row.id());
                           return proposition != null
                                   ? new ScoredProposition(proposition.getId(), proposition.getText(),
                                                           proposition.getConfidence(), proposition.getStatus().name(), row.score())
                                   : null;
                       })
                       .filter(r -> r != null)
                       .toList();
        } catch (Exception e) {
            logger.error("Scoped semantic search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * A proposition with its similarity score from a semantic search.
     */
    public record ScoredProposition(String id, String text, double confidence, String status, double score) {}

    /**
     * Update the memory tier of an anchor.
     *
     * @param id         the proposition ID
     * @param memoryTier the tier name (HOT, WARM, COLD)
     */
    @Transactional
    public void updateMemoryTier(@NonNull String id, @NonNull String memoryTier) {
        var cypher = """
                MATCH (p:Proposition {id: $id})
                SET p.memoryTier = $memoryTier
                """;
        var params = Map.of("id", id, "memoryTier", memoryTier);
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Updated memory tier for proposition {} to {}", id, memoryTier);
        } catch (Exception e) {
            logger.error("Failed to update memory tier for proposition {}: {}", id, e.getMessage(), e);
        }
    }

    /**
     * Migrate legacy anchors that lack a memoryTier property.
     * Computes tier from rank using the given thresholds.
     *
     * @param hotThreshold  rank >= this → HOT
     * @param warmThreshold rank >= this (and < hotThreshold) → WARM; below → COLD
     * @return number of anchors migrated
     */
    @Transactional
    public int migrateMemoryTiers(int hotThreshold, int warmThreshold) {
        var cypher = """
                MATCH (p:Proposition)
                WHERE p.rank > 0
                  AND coalesce(p.status, 'ACTIVE') IN ['ACTIVE', 'PROMOTED']
                  AND p.memoryTier IS NULL
                SET p.memoryTier = CASE
                    WHEN p.rank >= $hotThreshold THEN 'HOT'
                    WHEN p.rank >= $warmThreshold THEN 'WARM'
                    ELSE 'COLD'
                END
                RETURN count(p) AS migrated
                """;
        var params = Map.of("hotThreshold", hotThreshold, "warmThreshold", warmThreshold);
        try {
            var spec = QuerySpecification.withStatement(cypher).bind(params).transform(Long.class);
            var migrated = persistenceManager.getOne(spec);
            var count = migrated != null ? migrated.intValue() : 0;
            if (count > 0) {
                logger.info("Migrated memoryTier for {} legacy anchors", count);
            }
            return count;
        } catch (Exception e) {
            logger.warn("Failed to migrate memory tiers: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Update the pinned status of an anchor.
     *
     * @param id     the proposition ID
     * @param pinned the desired pinned state
     */
    @Transactional
    public void updatePinned(@NonNull String id, boolean pinned) {
        var cypher = """
                MATCH (p:Proposition {id: $id})
                SET p.pinned = $pinned
                """;
        var params = Map.of("id", id, "pinned", pinned);
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Updated pinned status for proposition {} to {}", id, pinned);
        } catch (Exception e) {
            logger.error("Failed to update pinned status for proposition {}: {}", id, e.getMessage(), e);
        }
    }

    /**
     * Create a supersession link between a successor and predecessor anchor.
     * Atomically creates the SUPERSEDES relationship and sets denormalized
     * fields on both nodes in a single Cypher statement.
     * <p>
     * <b>Alpha note:</b> This method assumes 1:1 supersession (one successor
     * replaces one predecessor). Fan-in (merge: multiple predecessors → one
     * successor) and fan-out (split: one predecessor → multiple successors)
     * are not yet modeled. The {@code supersedes}/{@code supersededBy} fields
     * store only the most recent link.
     *
     * @param successorId   the ID of the replacing anchor
     * @param predecessorId the ID of the replaced anchor
     * @param reason        why the supersession occurred
     */
    @Transactional
    public void createSupersessionLink(@NonNull String successorId, @NonNull String predecessorId,
                                       @NonNull SupersessionReason reason) {
        var cypher = """
                MATCH (successor:Proposition {id: $successorId})
                MATCH (predecessor:Proposition {id: $predecessorId})
                CREATE (successor)-[:SUPERSEDES {reason: $reason, occurredAt: $now}]->(predecessor)
                SET successor.supersedes = $predecessorId,
                    predecessor.supersededBy = $successorId
                """;
        var params = Map.of(
                "successorId", successorId,
                "predecessorId", predecessorId,
                "reason", reason.name(),
                "now", Instant.now().toString()
        );
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Created supersession link: {} supersedes {} (reason={})", successorId, predecessorId, reason);
        } catch (Exception e) {
            logger.error("Failed to create supersession link {} -> {}: {}", successorId, predecessorId, e.getMessage(), e);
        }
    }

    /**
     * Find anchors that were valid at a specific point in time.
     * Handles null temporal fields for backward compatibility with legacy nodes.
     *
     * @param contextId   the context to query
     * @param pointInTime the instant to check validity against
     * @return anchor IDs whose valid-time window contains the given instant, ordered by rank DESC
     */
    @Transactional(readOnly = true)
    public List<String> findValidAt(@NonNull String contextId, @NonNull Instant pointInTime) {
        var cypher = """
                MATCH (p:Proposition {contextId: $contextId})
                WHERE p.rank > 0
                  AND (p.validFrom IS NULL OR p.validFrom <= $pointInTime)
                  AND (p.validTo IS NULL OR p.validTo > $pointInTime)
                RETURN p.id AS id
                ORDER BY p.rank DESC
                """;
        var params = Map.of("contextId", contextId, "pointInTime", pointInTime.toString());
        try {
            return persistenceManager.query(
                    QuerySpecification.withStatement(cypher).bind(params).transform(String.class));
        } catch (Exception e) {
            logger.error("findValidAt query failed for context {}: {}", contextId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Walk the SUPERSEDES relationship chain from a given anchor, returning
     * the full ordered chain from oldest predecessor to newest successor.
     * Depth is bounded to 50 to prevent runaway traversal.
     *
     * @param anchorId the anchor to start from
     * @return ordered list of anchor IDs in the supersession chain (oldest first)
     */
    @Transactional(readOnly = true)
    public List<String> findSupersessionChain(@NonNull String anchorId) {
        var cypher = """
                MATCH path = (start:Proposition {id: $anchorId})
                              -[:SUPERSEDES*0..50]->(predecessor:Proposition)
                WHERE NOT (predecessor)-[:SUPERSEDES]->()
                WITH predecessor
                MATCH chain = (newest:Proposition)-[:SUPERSEDES*0..50]->(predecessor)
                WHERE NOT ()-[:SUPERSEDES]->(newest)
                RETURN [node IN nodes(chain) | node.id] AS chain
                """;
        var params = Map.of("anchorId", anchorId);
        try {
            var result = persistenceManager.query(
                    QuerySpecification.withStatement(cypher).bind(params).transform(List.class));
            if (result.isEmpty()) {
                return List.of(anchorId);
            }
            @SuppressWarnings("unchecked")
            var chain = (List<String>) result.getFirst();
            // SUPERSEDES points from newer to older, so nodes(chain) is newest-first
            // Reverse to get chronological order (oldest first)
            var reversed = new ArrayList<>(chain);
            java.util.Collections.reverse(reversed);
            return reversed;
        } catch (Exception e) {
            logger.error("findSupersessionChain query failed for anchor {}: {}", anchorId, e.getMessage(), e);
            return List.of(anchorId);
        }
    }

    /**
     * Find the predecessor anchor (the one this anchor superseded).
     * O(1) lookup via the denormalized {@code supersedes} field.
     *
     * @param anchorId the anchor to query
     * @return the predecessor's ID, or empty if no predecessor
     */
    @Transactional(readOnly = true)
    public Optional<String> findPredecessor(@NonNull String anchorId) {
        return findPropositionNodeById(anchorId)
                .map(PropositionNode::getSupersedes)
                .filter(id -> !id.isBlank());
    }

    /**
     * Find the successor anchor (the one that superseded this anchor).
     * O(1) lookup via the denormalized {@code supersededBy} field.
     *
     * @param anchorId the anchor to query
     * @return the successor's ID, or empty if not superseded
     */
    @Transactional(readOnly = true)
    public Optional<String> findSuccessor(@NonNull String anchorId) {
        return findPropositionNodeById(anchorId)
                .map(PropositionNode::getSupersededBy)
                .filter(id -> !id.isBlank());
    }

    // -------------------------------------------------------------------------
    // Canonization request persistence
    // -------------------------------------------------------------------------

    /**
     * Create a canonization request node with a relationship to the target proposition.
     *
     * @param id                 unique request ID
     * @param anchorId           the anchor whose authority is changing
     * @param contextId          the context the anchor belongs to
     * @param anchorText         snapshot of anchor text at request time
     * @param currentAuthority   authority at request creation
     * @param requestedAuthority target authority
     * @param reason             human-readable reason
     * @param requestedBy        actor requesting the change
     */
    @Transactional
    public void createCanonizationRequest(@NonNull String id, @NonNull String anchorId,
                                           @NonNull String contextId, @NonNull String anchorText,
                                           @NonNull String currentAuthority, @NonNull String requestedAuthority,
                                           @NonNull String reason, @NonNull String requestedBy) {
        var cypher = """
                CREATE (r:CanonizationRequest {
                    id: $id, anchorId: $anchorId, contextId: $contextId,
                    anchorText: $anchorText, currentAuthority: $currentAuthority,
                    requestedAuthority: $requestedAuthority, reason: $reason,
                    requestedBy: $requestedBy, status: 'PENDING',
                    createdAt: toString(datetime())
                })
                WITH r
                MATCH (p:Proposition {id: $anchorId})
                CREATE (r)-[:CANONIZATION_REQUEST_FOR]->(p)
                RETURN {id: r.id} AS result
                """;
        var params = new HashMap<String, Object>();
        params.put("id", id);
        params.put("anchorId", anchorId);
        params.put("contextId", contextId);
        params.put("anchorText", anchorText);
        params.put("currentAuthority", currentAuthority);
        params.put("requestedAuthority", requestedAuthority);
        params.put("reason", reason);
        params.put("requestedBy", requestedBy);
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Created canonization request {} for anchor {}", id, anchorId);
        } catch (Exception e) {
            logger.error("Failed to create canonization request {} for anchor {}: {}", id, anchorId, e.getMessage(), e);
        }
    }

    /**
     * Find all pending canonization requests for a specific context.
     *
     * @param contextId the context to query
     * @return list of request data maps
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findPendingCanonizationRequests(@NonNull String contextId) {
        var cypher = """
                MATCH (r:CanonizationRequest {contextId: $contextId, status: 'PENDING'})
                RETURN {id: r.id, anchorId: r.anchorId, contextId: r.contextId,
                        anchorText: r.anchorText, currentAuthority: r.currentAuthority,
                        requestedAuthority: r.requestedAuthority, reason: r.reason,
                        requestedBy: r.requestedBy, status: r.status,
                        createdAt: toString(r.createdAt)} AS result
                """;
        var params = Map.of("contextId", contextId);
        try {
            @SuppressWarnings("unchecked")
            var rows = (List<Map<String, Object>>) (List<?>) persistenceManager.query(
                    QuerySpecification.withStatement(cypher).bind(params).transform(Map.class));
            return rows;
        } catch (Exception e) {
            logger.warn("findPendingCanonizationRequests query failed for context {}: {}", contextId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Find a pending canonization request for a specific anchor and requested authority.
     * Used for idempotency checks.
     *
     * @param anchorId           the anchor ID
     * @param requestedAuthority the requested authority level
     * @return the request data map if found
     */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> findPendingCanonizationRequest(@NonNull String anchorId,
                                                                         @NonNull String requestedAuthority) {
        var cypher = """
                MATCH (r:CanonizationRequest {anchorId: $anchorId, requestedAuthority: $requestedAuthority, status: 'PENDING'})
                RETURN {id: r.id, anchorId: r.anchorId, contextId: r.contextId,
                        anchorText: r.anchorText, currentAuthority: r.currentAuthority,
                        requestedAuthority: r.requestedAuthority, reason: r.reason,
                        requestedBy: r.requestedBy, status: r.status,
                        createdAt: toString(r.createdAt)} AS result
                LIMIT 1
                """;
        var params = Map.of("anchorId", anchorId, "requestedAuthority", requestedAuthority);
        try {
            @SuppressWarnings("unchecked")
            var rows = (List<Map<String, Object>>) (List<?>) persistenceManager.query(
                    QuerySpecification.withStatement(cypher).bind(params).transform(Map.class));
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
        } catch (Exception e) {
            logger.warn("findPendingCanonizationRequest query failed for anchor {}: {}", anchorId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Resolve a canonization request (approve/reject) with audit trail fields.
     *
     * @param requestId  the request ID
     * @param newStatus  the new status (APPROVED, REJECTED, STALE)
     * @param resolvedBy identifier of the resolving actor
     * @param note       optional resolution note
     */
    @Transactional
    public void resolveCanonizationRequest(@NonNull String requestId, @NonNull String newStatus,
                                            @Nullable String resolvedBy, @Nullable String note) {
        var cypher = """
                MATCH (r:CanonizationRequest {id: $id})
                SET r.status = $newStatus,
                    r.resolvedAt = toString(datetime()),
                    r.resolvedBy = $resolvedBy,
                    r.resolutionNote = $note
                """;
        var params = new HashMap<String, Object>();
        params.put("id", requestId);
        params.put("newStatus", newStatus);
        params.put("resolvedBy", resolvedBy);
        params.put("note", note);
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Resolved canonization request {} as {}", requestId, newStatus);
        } catch (Exception e) {
            logger.error("Failed to resolve canonization request {}: {}", requestId, e.getMessage(), e);
        }
    }

    /**
     * Find a canonization request by its ID (any status).
     *
     * @param requestId the request ID
     * @return the request data map if found
     */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> findCanonizationRequestById(@NonNull String requestId) {
        var cypher = """
                MATCH (r:CanonizationRequest {id: $id})
                RETURN {id: r.id, anchorId: r.anchorId, contextId: r.contextId,
                        anchorText: r.anchorText, currentAuthority: r.currentAuthority,
                        requestedAuthority: r.requestedAuthority, reason: r.reason,
                        requestedBy: r.requestedBy, status: r.status,
                        createdAt: toString(r.createdAt),
                        resolvedAt: toString(r.resolvedAt),
                        resolvedBy: r.resolvedBy,
                        resolutionNote: r.resolutionNote} AS result
                """;
        var params = Map.of("id", requestId);
        try {
            @SuppressWarnings("unchecked")
            var rows = (List<Map<String, Object>>) (List<?>) persistenceManager.query(
                    QuerySpecification.withStatement(cypher).bind(params).transform(Map.class));
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
        } catch (Exception e) {
            logger.warn("findCanonizationRequestById query failed for request {}: {}", requestId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Find all pending canonization requests across all contexts.
     *
     * @return list of pending request data maps
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findAllPendingCanonizationRequests() {
        var cypher = """
                MATCH (r:CanonizationRequest {status: 'PENDING'})
                RETURN {id: r.id, anchorId: r.anchorId, contextId: r.contextId,
                        anchorText: r.anchorText, currentAuthority: r.currentAuthority,
                        requestedAuthority: r.requestedAuthority, reason: r.reason,
                        requestedBy: r.requestedBy, status: r.status,
                        createdAt: toString(r.createdAt)} AS result
                """;
        try {
            @SuppressWarnings("unchecked")
            var rows = (List<Map<String, Object>>) (List<?>) persistenceManager.query(
                    QuerySpecification.withStatement(cypher).transform(Map.class));
            return rows;
        } catch (Exception e) {
            logger.warn("findAllPendingCanonizationRequests query failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Mark all PENDING canonization requests for a context as STALE.
     * Used during context teardown to prevent orphaned pending requests.
     *
     * @param contextId the context to clean up
     */
    @Transactional
    public void markContextRequestsStale(@NonNull String contextId) {
        var cypher = """
                MATCH (r:CanonizationRequest {contextId: $contextId, status: 'PENDING'})
                SET r.status = 'STALE',
                    r.resolvedAt = toString(datetime())
                """;
        var params = Map.of("contextId", contextId);
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Marked pending canonization requests as STALE for context {}", contextId);
        } catch (Exception e) {
            logger.warn("Failed to mark canonization requests as stale for context {}: {}", contextId, e.getMessage());
        }
    }
}
