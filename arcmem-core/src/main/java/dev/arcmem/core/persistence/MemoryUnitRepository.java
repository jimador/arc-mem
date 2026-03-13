package dev.arcmem.core.persistence;
import dev.arcmem.core.memory.budget.*;
import dev.arcmem.core.memory.canon.*;
import dev.arcmem.core.memory.conflict.*;
import dev.arcmem.core.memory.engine.*;
import dev.arcmem.core.memory.maintenance.*;
import dev.arcmem.core.memory.model.*;
import dev.arcmem.core.memory.mutation.*;
import dev.arcmem.core.memory.trust.*;
import dev.arcmem.core.assembly.budget.*;
import dev.arcmem.core.assembly.compaction.*;
import dev.arcmem.core.assembly.compliance.*;
import dev.arcmem.core.assembly.protection.*;
import dev.arcmem.core.assembly.retrieval.*;

import com.embabel.agent.rag.service.RetrievableIdentifier;
import dev.arcmem.core.memory.event.SupersessionReason;
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
 * Drivine-backed repository for propositions that also supports unit promotion,
 * reinforcement, eviction, and authority upgrades.
 * <p>
 * Implements PropositionRepository so it can serve as the primary store for DICE
 * extraction while extending the contract with unit-lifecycle methods.
 * <p>
 * Invariant A1: rank is always clamped to [100, 900] on write when non-zero.
 * Invariant A2: pinned units are never evicted by {@link #evictLowestRanked}.
 * Authority transitions (both promotion and demotion) are applied via setAuthority(). Business rules live in ArcMemEngine.
 */
@Service
public class MemoryUnitRepository implements PropositionRepository {

    private static final Logger logger = LoggerFactory.getLogger(MemoryUnitRepository.class);
    private static final String PROPOSITION_VECTOR_INDEX = "proposition_embedding_index";

    private final GraphObjectManager graphObjectManager;
    private final PersistenceManager persistenceManager;
    private final EmbeddingService embeddingService;
    private final dev.arcmem.core.config.ArcMemProperties properties;

    public MemoryUnitRepository(
            GraphObjectManager graphObjectManager,
            PersistenceManager persistenceManager,
            EmbeddingService embeddingService,
            dev.arcmem.core.config.ArcMemProperties properties) {
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
        normalizeLegacyUnitStatuses();
        migrateMemoryTiers(
                properties.unit().tier() != null ? properties.unit().tier().hotThreshold() : 600,
                properties.unit().tier() != null ? properties.unit().tier().warmThreshold() : 350);
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

    @Override
    public @NonNull String getLuceneSyntaxNotes() {
        return "fully supported";
    }

    @Override
    @Transactional
    public @NonNull Proposition save(@NonNull Proposition proposition) {
        var view = PropositionView.fromDice(proposition);
        try {
            graphObjectManager.save(view, CascadeType.NONE);
        } catch (Exception e) {
            logger.error("graphObjectManager.save() failed for proposition {}: {}",
                    proposition.getId(), e.getMessage(), e);
            throw e;
        }

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
     * Save a PropositionNode directly (used for manual unit injection).
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
     * including unit fields (rank, authority, pinned, reinforcementCount).
     * Prefer this over {@link #findById} when unit-specific fields are needed.
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

    CypherQuery buildCypher(@NonNull PropositionQuery query) {
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
     * @param sourceId       source identifier (e.g., "system", "user-alice", "external-api")
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
     * Find active, non-unit propositions for prompt assembly.
     * NonUnit propositions are those with rank 0.
     *
     * @param contextId the context ID
     * @param limit maximum propositions to return; values <= 0 return empty
     *
     * @return propositions ordered by confidence/revision recency
     */
    @Transactional(readOnly = true)
    public @NonNull List<PropositionNode> findActiveUnpromotedPropositions(@NonNull String contextId, int limit) {
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
            logger.warn("findActiveUnpromotedPropositions query failed for context {}: {}", contextId, e.getMessage());
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

    private void normalizeLegacyUnitStatuses() {
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

        var deleteMentionsSpec = QuerySpecification
                .withStatement("MATCH (m:Mention) DETACH DELETE m");
        persistenceManager.execute(deleteMentionsSpec);

        logger.info("Deleted {} propositions and orphaned mentions", count);
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

        var deleteMentionsSpec = QuerySpecification
                .withStatement("MATCH (p:Proposition {contextId: $contextId})-[:HAS_MENTION]->(m:Mention) DETACH DELETE m")
                .bind(Map.of("contextId", contextId));
        persistenceManager.execute(deleteMentionsSpec);

        var deleteSpec = QuerySpecification
                .withStatement("MATCH (p:Proposition {contextId: $contextId}) DETACH DELETE p")
                .bind(Map.of("contextId", contextId));
        persistenceManager.execute(deleteSpec);

        logger.info("Deleted {} propositions and related mentions for context {}", count, contextId);
        return count.intValue();
    }

    /**
     * Find active units for the given context, ordered by rank descending.
     * Only returns propositions that have been promoted (rank > 0) and are ACTIVE.
     *
     * @param contextId the conversation or session context
     *
     * @return list of active unit nodes ordered highest-rank first
     */
    @Transactional(readOnly = true)
    public List<PropositionNode> findActiveUnits(@NonNull String contextId) {
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
            logger.warn("findActiveUnits query failed for context {}: {}", contextId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Find all units (active and archived) for the given context.
     * Units are identified by non-null authority or rank > 0.
     */
    @Transactional(readOnly = true)
    public List<PropositionNode> findUnitsByContext(@NonNull String contextId) {
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
            logger.warn("findUnitsByContext query failed for context {}: {}", contextId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Find pinned units for the given context, ordered by rank descending.
     * Pinned units are immune to eviction.
     *
     * @param contextId the conversation or session context
     *
     * @return list of pinned unit nodes ordered highest-rank first
     */
    @Transactional(readOnly = true)
    public List<PropositionNode> findPinnedUnits(@NonNull String contextId) {
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
            logger.warn("findPinnedUnits query failed for context {}: {}", contextId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Promote a proposition to unit status by setting its rank and authority.
     * Rank is clamped to [100, 900]. Sets lastReinforced to now.
     *
     * @param propositionId the ID of the proposition to promote
     * @param rank          desired rank, clamped to [100, 900]
     * @param authority     authority level: PROVISIONAL, UNRELIABLE, RELIABLE, or CANON
     */
    @Transactional
    public void promoteToUnit(@NonNull String propositionId, int rank, @NonNull String authority) {
        promoteToUnit(propositionId, rank, authority, null, null);
    }

    @Transactional
    public void promoteToUnit(@NonNull String propositionId, int rank, @NonNull String authority,
                                @Nullable String memoryTier) {
        promoteToUnit(propositionId, rank, authority, memoryTier, null);
    }

    @Transactional
    public void promoteToUnit(@NonNull String propositionId, int rank, @NonNull String authority,
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
            logger.debug("Promoted proposition {} to unit with rank={} authority={} tier={} ceiling={}",
                    propositionId, clamped, authority, memoryTier, authorityCeiling);
        } catch (Exception e) {
            logger.error("Failed to promote proposition {} to unit: {}", propositionId, e.getMessage(), e);
        }
    }

    /**
     * Update the rank of an unit, clamped to [100, 900].
     * Has no effect on propositions that are not currently units.
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
     * Enable an unit by setting status ACTIVE and ensuring a non-zero rank.
     *
     * @param id   the proposition ID
     * @param rank desired rank; clamped to [100, 900]
     */
    @Transactional
    public void activateUnit(@NonNull String id, int rank) {
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
            logger.debug("Activated unit {} with rank {}", id, clamped);
        } catch (Exception e) {
            logger.error("Failed to activate unit {}: {}", id, e.getMessage(), e);
        }
    }

    /**
     * Disable an unit by setting its status to SUPERSEDED (rank preserved).
     *
     * @param id the proposition ID
     */
    @Transactional
    public void deactivateUnit(@NonNull String id) {
        var cypher = """
                MATCH (p:Proposition {id: $id})
                SET p.status = 'SUPERSEDED'
                """;
        var params = Map.of("id", id);
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Deactivated unit {}", id);
        } catch (Exception e) {
            logger.error("Failed to deactivate unit {}: {}", id, e.getMessage(), e);
        }
    }

    /**
     * Reinforce an unit: increments reinforcement count and updates lastReinforced timestamp.
     *
     * @param id the proposition ID
     */
    @Transactional
    public void reinforceUnit(@NonNull String id) {
        var cypher = """
                MATCH (p:Proposition {id: $id})
                SET p.reinforcementCount = p.reinforcementCount + 1,
                    p.lastReinforced = $now
                """;
        var params = Map.of("id", id, "now", Instant.now().toString());
        try {
            persistenceManager.execute(QuerySpecification.withStatement(cypher).bind(params));
            logger.debug("Reinforced unit {}", id);
        } catch (Exception e) {
            logger.error("Failed to reinforce unit {}: {}", id, e.getMessage(), e);
        }
    }

    /**
     * Evict lowest-ranked non-pinned units when the active unit count exceeds budget.
     * Evicted units have their status set to SUPERSEDED and rank set to 0.
     *
     * @param contextId the conversation or session context
     * @param budget    the maximum number of active units allowed
     *
     * @return list of evicted units (ID and previous rank), empty if within budget
     */
    @Transactional
    public List<MemoryUnitEvictionInfo> evictLowestRanked(@NonNull String contextId, int budget) {
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
            logger.error("Failed to count active units for eviction in context {}: {}", contextId, e.getMessage(), e);
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
                RETURN {unitId: p.id, rank: previousRank} AS result
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
                        var unitId = (String) m.get("unitId");
                        var rank = ((Number) m.get("rank")).intValue();
                        return new MemoryUnitEvictionInfo(unitId, rank);
                    })
                    .toList();
            logger.info("Evicted {} units from context {} (budget={}, was={})", evicted.size(), contextId, budget, current);
            return evicted;
        } catch (Exception e) {
            logger.error("Failed to evict units for context {}: {}", contextId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Count active units for the given context.
     * Active units are propositions with rank > 0 and status ACTIVE.
     *
     * @param contextId the conversation or session context
     *
     * @return the number of active units
     */
    @Transactional(readOnly = true)
    public int countActiveUnits(@NonNull String contextId) {
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
            logger.error("Failed to count active units for context {}: {}", contextId, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Set the authority of an unit to any level — supports both promotion and demotion.
     * Business rules for transition validation live in {@link dev.arcmem.core.memory.engine.ArcMemEngine},
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
     * Archive an unit by setting its status to SUPERSEDED and rank to 0.
     *
     * @param id the proposition ID
     */
    @Transactional
    public void archiveUnit(@NonNull String id) {
        archiveUnit(id, null);
    }

    /**
     * Archive an unit, optionally recording the successor that replaced it.
     *
     * @param id          the proposition ID to archive
     * @param successorId the ID of the replacing unit, or null if no successor
     */
    @Transactional
    public void archiveUnit(@NonNull String id, @Nullable String successorId) {
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
            if (successorId != null) {
                logger.debug("Archived unit {} (superseded by {})", id, successorId);
            } else {
                logger.debug("Archived unit {}", id);
            }
        } catch (Exception e) {
            logger.error("Failed to archive unit {}: {}", id, e.getMessage(), e);
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
     * Update the memory tier of an unit.
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
     * Migrate legacy units that lack a memoryTier property.
     * Computes tier from rank using the given thresholds.
     *
     * @param hotThreshold  rank >= this → HOT
     * @param warmThreshold rank >= this (and < hotThreshold) → WARM; below → COLD
     * @return number of units migrated
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
                logger.info("Migrated memoryTier for {} legacy units", count);
            }
            return count;
        } catch (Exception e) {
            logger.warn("Failed to migrate memory tiers: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Update the pinned status of an unit.
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
     * Create a supersession link between a successor and predecessor unit.
     * Atomically creates the SUPERSEDES relationship and sets denormalized
     * fields on both nodes in a single Cypher statement.
     * <p>
     * <b>Alpha note:</b> This method assumes 1:1 supersession (one successor
     * replaces one predecessor). Fan-in (merge: multiple predecessors → one
     * successor) and fan-out (split: one predecessor → multiple successors)
     * are not yet modeled. The {@code supersedes}/{@code supersededBy} fields
     * store only the most recent link.
     *
     * @param successorId   the ID of the replacing unit
     * @param predecessorId the ID of the replaced unit
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
     * Find units that were valid at a specific point in time.
     * Handles null temporal fields for backward compatibility with legacy nodes.
     *
     * @param contextId   the context to query
     * @param pointInTime the instant to check validity against
     * @return unit IDs whose valid-time window contains the given instant, ordered by rank DESC
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
     * Walk the SUPERSEDES relationship chain from a given unit, returning
     * the full ordered chain from oldest predecessor to newest successor.
     * Depth is bounded to 50 to prevent runaway traversal.
     *
     * @param unitId the unit to start from
     * @return ordered list of unit IDs in the supersession chain (oldest first)
     */
    @Transactional(readOnly = true)
    public List<String> findSupersessionChain(@NonNull String unitId) {
        var cypher = """
                MATCH path = (start:Proposition {id: $unitId})
                              -[:SUPERSEDES*0..50]->(predecessor:Proposition)
                WHERE NOT (predecessor)-[:SUPERSEDES]->()
                WITH predecessor
                MATCH chain = (newest:Proposition)-[:SUPERSEDES*0..50]->(predecessor)
                WHERE NOT ()-[:SUPERSEDES]->(newest)
                RETURN [node IN nodes(chain) | node.id] AS chain
                """;
        var params = Map.of("unitId", unitId);
        try {
            var result = persistenceManager.query(
                    QuerySpecification.withStatement(cypher).bind(params).transform(List.class));
            if (result.isEmpty()) {
                return List.of(unitId);
            }
            @SuppressWarnings("unchecked")
            var chain = (List<String>) result.getFirst();
            // SUPERSEDES points from newer to older, so nodes(chain) is newest-first
            // Reverse to get chronological order (oldest first)
            var reversed = new ArrayList<>(chain);
            java.util.Collections.reverse(reversed);
            return reversed;
        } catch (Exception e) {
            logger.error("findSupersessionChain query failed for unit {}: {}", unitId, e.getMessage(), e);
            return List.of(unitId);
        }
    }

    /**
     * Find the predecessor unit (the one this unit superseded).
     * O(1) lookup via the denormalized {@code supersedes} field.
     *
     * @param unitId the unit to query
     * @return the predecessor's ID, or empty if no predecessor
     */
    @Transactional(readOnly = true)
    public Optional<String> findPredecessor(@NonNull String unitId) {
        return findPropositionNodeById(unitId)
                .map(PropositionNode::getSupersedes)
                .filter(id -> !id.isBlank());
    }

    /**
     * Find the successor unit (the one that superseded this unit).
     * O(1) lookup via the denormalized {@code supersededBy} field.
     *
     * @param unitId the unit to query
     * @return the successor's ID, or empty if not superseded
     */
    @Transactional(readOnly = true)
    public Optional<String> findSuccessor(@NonNull String unitId) {
        return findPropositionNodeById(unitId)
                .map(PropositionNode::getSupersededBy)
                .filter(id -> !id.isBlank());
    }
}
