package dev.dunnam.diceanchors.sim.engine;

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.rag.model.Chunk;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.dice.common.EntityResolver;
import com.embabel.dice.common.SourceAnalysisContext;
import com.embabel.dice.pipeline.PropositionPipeline;
import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionRepository;
import dev.dunnam.diceanchors.extract.AnchorPromoter;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs DICE proposition extraction on DM responses during simulation turns.
 * <p>
 * Wraps the extraction pipeline for the simulation context: extract propositions
 * from a DM response, persist them via the repository, then evaluate for anchor
 * promotion through {@link AnchorPromoter}.
 * <p>
 * This service is only invoked when {@code scenario.isExtractionEnabled()} is true.
 */
@Service
public class SimulationExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(SimulationExtractionService.class);

    private final PropositionPipeline pipeline;
    private final PropositionRepository propositionRepository;
    private final AnchorRepository anchorRepository;
    private final AnchorPromoter promoter;
    private final DataDictionary dataDictionary;
    private final EntityResolver entityResolver;
    private final ObjectProvider<NamedEntityDataRepository> namedEntityRepositoryProvider;

    public SimulationExtractionService(
            PropositionPipeline pipeline,
            PropositionRepository propositionRepository,
            AnchorRepository anchorRepository,
            AnchorPromoter promoter,
            DataDictionary dataDictionary,
            EntityResolver entityResolver,
            ObjectProvider<NamedEntityDataRepository> namedEntityRepositoryProvider) {
        this.pipeline = pipeline;
        this.propositionRepository = propositionRepository;
        this.anchorRepository = anchorRepository;
        this.promoter = promoter;
        this.dataDictionary = dataDictionary;
        this.entityResolver = entityResolver;
        this.namedEntityRepositoryProvider = namedEntityRepositoryProvider;
    }

    /**
     * Extract propositions from a DM response, persist them, and evaluate for promotion.
     *
     * @param contextId      the simulation-scoped context ID
     * @param dmResponseText the DM's response text to extract propositions from
     * @return extraction results including counts and extracted texts
     */
    public ExtractionResult extract(String contextId, String dmResponseText) {
        if (dmResponseText == null || dmResponseText.isBlank()) {
            logger.debug("Skipping extraction for context {} — empty DM response", contextId);
            return ExtractionResult.empty();
        }

        var context = SourceAnalysisContext
                .withContextId(contextId)
                .withEntityResolver(entityResolver)
                .withSchema(dataDictionary);

        try {
            var chunk = new SimChunk(UUID.randomUUID().toString(), dmResponseText);
            var results = pipeline.process(List.of(chunk), context);
            var propositions = results.propositionsToPersist();

            if (propositions == null || propositions.isEmpty()) {
                logger.debug("No propositions extracted from DM response for context {}", contextId);
                return ExtractionResult.empty();
            }

            logger.info("Extracted {} propositions from DM response for context {}",
                    propositions.size(), contextId);

            // Persist extracted propositions and entity links when available.
            // If full persist fails, degrade to proposition-only persistence so
            // anchor promotion can still proceed.
            var namedEntityRepository = namedEntityRepositoryProvider.getIfAvailable();
            if (namedEntityRepository != null) {
                try {
                    results.persist(propositionRepository, namedEntityRepository);
                } catch (RuntimeException e) {
                    logger.warn("Full DICE persist failed for context {}, falling back to proposition-only save: {}",
                            contextId, e.getMessage());
                    propositionRepository.saveAll(propositions);
                }
            } else {
                propositionRepository.saveAll(propositions);
                logger.debug("NamedEntityDataRepository unavailable; persisted propositions only for context {}", contextId);
            }

            // Some extraction flows may emit propositions without the target context set.
            // Normalize the persisted rows so downstream anchor injection and UI filters
            // operate on the current simulation context.
            var propositionIds = propositions.stream()
                                             .map(Proposition::getId)
                                             .toList();
            var updated = anchorRepository.assignContextIds(propositionIds, contextId);
            logger.debug("Assigned context {} to {} extracted propositions", contextId, updated);

            // Tag all DM-extracted propositions with "dm" source so
            // SourceAuthoritySignal scores them at 0.9 instead of the 0.5
            // default for unknown sources. Without this, trust scores are
            // too low for auto-promotion under any profile.
            var tagged = anchorRepository.tagSourceIds(propositionIds, "dm");
            logger.debug("Tagged {} propositions with 'dm' source for context {}", tagged, contextId);

            // Evaluate for anchor promotion
            var promoted = promoter.evaluateAndPromote(contextId, propositions);

            var extractedTexts = propositions.stream()
                    .map(Proposition::getText)
                    .toList();

            return new ExtractionResult(propositions.size(), promoted, extractedTexts);
        } catch (Exception e) {
            logger.error("DICE extraction failed for context {}: {}", contextId, e.getMessage(), e);
            return ExtractionResult.empty();
        }
    }

    /**
     * Simple Chunk implementation wrapping a DM response text for DICE extraction.
     * Provides the minimum Chunk contract needed by the extraction pipeline.
     */
    private static final class SimChunk implements Chunk {
        private final String id;
        private final String text;

        SimChunk(String id, String text) {
            this.id = id;
            this.text = text;
        }

        @Override
        public @NonNull String getId() {
            return id;
        }

        @Override
        public @NonNull String getText() {
            return text;
        }

        @Override
        public @Nullable String getParentId() {
            return null;
        }

        @Override
        public @NonNull String getUrtext() {
            return text;
        }

        @Override
        public @NonNull Map<String, Object> getMetadata() {
            return Map.of();
        }

        @Override
        public @NonNull Chunk withAdditionalMetadata(@NonNull Map<String, ?> metadata) {
            return this;
        }
    }
}
