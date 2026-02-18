package dev.dunnam.diceanchors.chat;

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.rag.service.NamedEntityDataRepository;
import com.embabel.dice.common.EntityResolver;
import com.embabel.dice.common.SourceAnalysisContext;
import com.embabel.dice.incremental.ChunkHistoryStore;
import com.embabel.dice.incremental.ConversationSource;
import com.embabel.dice.incremental.IncrementalAnalyzer;
import com.embabel.dice.incremental.MessageFormatter;
import com.embabel.dice.incremental.WindowConfig;
import com.embabel.dice.incremental.proposition.PropositionIncrementalAnalyzer;
import com.embabel.dice.pipeline.ChunkPropositionResult;
import com.embabel.dice.pipeline.PropositionPipeline;
import com.embabel.dice.proposition.PropositionRepository;
import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.extract.AnchorPromoter;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Async listener that extracts propositions from chat conversations
 * and promotes eligible ones to anchors.
 * <p>
 * Follows the Impromptu {@code ConversationPropositionExtraction} pattern:
 * uses {@link PropositionIncrementalAnalyzer} for windowed, deduplicated
 * processing, then evaluates each proposition for anchor promotion via
 * {@link AnchorPromoter} (trust-gated when the trust pipeline is active).
 */
@Service
public class ConversationPropositionExtraction {

    private static final Logger logger = LoggerFactory.getLogger(ConversationPropositionExtraction.class);

    @SuppressWarnings("rawtypes")
    private final IncrementalAnalyzer analyzer;
    private final DataDictionary dataDictionary;
    private final PropositionRepository propositionRepository;
    private final AnchorRepository anchorRepository;
    private final EntityResolver entityResolver;
    private final AnchorPromoter anchorPromoter;
    private final ObjectProvider<NamedEntityDataRepository> namedEntityRepositoryProvider;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public ConversationPropositionExtraction(
            PropositionPipeline propositionPipeline,
            ChunkHistoryStore chunkHistoryStore,
            DataDictionary dataDictionary,
            PropositionRepository propositionRepository,
            AnchorRepository anchorRepository,
            EntityResolver entityResolver,
            AnchorPromoter anchorPromoter,
            ObjectProvider<NamedEntityDataRepository> namedEntityRepositoryProvider,
            DiceAnchorsProperties properties) {
        this.dataDictionary = dataDictionary;
        this.propositionRepository = propositionRepository;
        this.anchorRepository = anchorRepository;
        this.entityResolver = entityResolver;
        this.anchorPromoter = anchorPromoter;
        this.namedEntityRepositoryProvider = namedEntityRepositoryProvider;

        var memory = properties.memory();
        var config = new WindowConfig(
                memory.windowSize(),
                memory.windowOverlap(),
                memory.triggerInterval()
        );
        this.analyzer = new PropositionIncrementalAnalyzer<>(
                propositionPipeline,
                chunkHistoryStore,
                MessageFormatter.INSTANCE,
                config
        );
    }

    @Async
    @EventListener
    public void onConversationExchange(ConversationAnalysisRequestEvent event) {
        var contextId = event.getContextId();

        var messages = event.getConversation().getMessages();
        logger.debug("DICE extraction triggered for context {} with {} messages", contextId, messages.size());
        if (messages.size() < 2) {
            logger.debug("Not enough messages for extraction (need at least 2)");
            return;
        }

        var context = SourceAnalysisContext
                .withContextId(contextId)
                .withEntityResolver(entityResolver)
                .withSchema(dataDictionary);

        var source = new ConversationSource(event.getConversation());

        Object rawResult;
        try {
            logger.debug("Running incremental analysis for context {} ...", contextId);
            rawResult = analyzer.analyze(source, context);
        } catch (ClassCastException e) {
            logger.error("Type mismatch during DICE analysis for context {}: {}", contextId, e.getMessage());
            return;
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument during DICE analysis for context {}: {}", contextId, e.getMessage());
            return;
        } catch (RuntimeException e) {
            logger.error("Unexpected error during DICE analysis for context {}", contextId, e);
            return;
        }

        if (rawResult == null) {
            logger.debug("Analysis returned null for context {} (window not triggered or already processed)", contextId);
            return;
        }
        logger.debug("Analysis returned result type {} for context {}", rawResult.getClass().getName(), contextId);

        if (!(rawResult instanceof ChunkPropositionResult result)) {
            logger.error("Unexpected analysis result type for context {}: {}", contextId, rawResult.getClass().getName());
            return;
        }

        if (result.getPropositions().isEmpty()) {
            logger.debug("No propositions extracted for context {}", contextId);
            return;
        }
        var propositionsToPersist = result.propositionsToPersist();
        if (propositionsToPersist.isEmpty()) {
            logger.debug("No propositions to persist for context {}", contextId);
            return;
        }

        logger.info("Extracted {} propositions for context {}",
                    propositionsToPersist.size(), contextId);

        // Persist extracted propositions
        try {
            var namedEntityRepository = namedEntityRepositoryProvider.getIfAvailable();
            if (namedEntityRepository != null) {
                try {
                    result.persist(propositionRepository, namedEntityRepository);
                } catch (RuntimeException e) {
                    logger.warn("Full DICE persist failed for context {}, falling back to proposition-only save: {}",
                            contextId, e.getMessage());
                    propositionRepository.saveAll(propositionsToPersist);
                }
            } else {
                propositionRepository.saveAll(propositionsToPersist);
                logger.debug("NamedEntityDataRepository unavailable; persisted propositions only for context {}", contextId);
            }
        } catch (IllegalStateException e) {
            logger.error("Failed to persist propositions for context {}: {}", contextId, e.getMessage());
            return;
        } catch (RuntimeException e) {
            logger.error("Unexpected error persisting propositions for context {}", contextId, e);
            return;
        }

        var propositionIds = propositionsToPersist.stream()
                                                  .map(com.embabel.dice.proposition.Proposition::getId)
                                                  .toList();
        var updated = anchorRepository.assignContextIds(propositionIds, contextId);
        logger.debug("Assigned context {} to {} extracted propositions", contextId, updated);

        // Evaluate for anchor promotion (trust-gated)
        try {
            var promoted = anchorPromoter.evaluateAndPromote(contextId, propositionsToPersist);
            if (promoted > 0) {
                logger.info("Promoted {} propositions to anchors for context {}", promoted, contextId);
            }
        } catch (RuntimeException e) {
            logger.error("Anchor promotion failed for context {}", contextId, e);
        }
    }
}
