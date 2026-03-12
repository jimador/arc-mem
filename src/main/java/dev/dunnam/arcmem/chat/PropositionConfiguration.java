package dev.dunnam.diceanchors.chat;

import com.embabel.agent.api.common.AiBuilder;
import com.embabel.agent.core.DataDictionary;
import com.embabel.dice.common.EntityResolver;
import com.embabel.dice.common.SchemaAdherence;
import com.embabel.dice.common.SchemaRegistry;
import com.embabel.dice.common.resolver.InMemoryEntityResolver;
import com.embabel.dice.common.support.InMemorySchemaRegistry;
import com.embabel.dice.incremental.ChunkHistoryStore;
import com.embabel.dice.pipeline.PropositionPipeline;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.extraction.LlmPropositionExtractor;
import com.embabel.dice.proposition.revision.LlmPropositionReviser;
import com.embabel.dice.proposition.revision.PropositionReviser;
import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.domain.Character;
import dev.dunnam.diceanchors.domain.Creature;
import dev.dunnam.diceanchors.domain.DndItem;
import dev.dunnam.diceanchors.domain.DndLocation;
import dev.dunnam.diceanchors.domain.Faction;
import dev.dunnam.diceanchors.domain.StoryEvent;
import dev.dunnam.diceanchors.persistence.DiceAnchorsChunkHistoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configures the DICE proposition extraction pipeline for the D&D campaign domain.
 * <p>
 * Follows the Impromptu {@code PropositionConfiguration} and tor {@code DiceConfiguration}
 * patterns, adapted for the dice-anchors demo:
 * <ul>
 *   <li>D&D entity types replace music domain types</li>
 *   <li>{@code AnchorRepository} serves as the {@link PropositionRepository} (Neo4j-backed)</li>
 *   <li>No graph projection layer — propositions are stored directly via Drivine</li>
 * </ul>
 * <p>
 * Async extraction is enabled; {@link ConversationAnalysisRequestEvent} listeners
 * drive the pipeline on each turn.
 */
@Configuration
@EnableAsync
public class PropositionConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(PropositionConfiguration.class);

    /**
     * D&D campaign data dictionary — the schema DICE uses for entity extraction.
     * Entity interfaces are annotated with {@code @JsonClassDescription} so the
     * LLM understands each entity type during proposition extraction.
     */
    @Bean
    @Primary
    DataDictionary dndDataDictionary() {
        var schema = DataDictionary.fromClasses(
                "dice-anchors",
                Character.class,
                DndLocation.class,
                DndItem.class,
                Faction.class,
                Creature.class,
                StoryEvent.class
        );
        logger.info("Created D&D data dictionary with {} entity types", schema.getDomainTypes().size());
        return schema;
    }

    /**
     * DICE schema registry — maps the data dictionary for schema-adherent extraction.
     */
    @Bean
    SchemaRegistry dndSchemaRegistry(DataDictionary dndDataDictionary) {
        var registry = new InMemorySchemaRegistry(dndDataDictionary);
        registry.register(dndDataDictionary);
        return registry;
    }

    /**
     * In-memory entity resolver — resolves named entity references during extraction.
     * A persistent resolver backed by Neo4j can replace this when entity tracking is needed.
     */
    @Bean
    EntityResolver dndEntityResolver() {
        return new InMemoryEntityResolver();
    }

    /**
     * Exposes the DICE {@link ChunkHistoryStore} from the project's
     * {@link DiceAnchorsChunkHistoryStore} wrapper for injection into
     * the incremental analysis pipeline.
     */
    @Bean
    ChunkHistoryStore chunkHistoryStore(DiceAnchorsChunkHistoryStore store) {
        return store.delegate();
    }

    /**
     * LLM-based proposition extractor for D&D conversations.
     * Uses the extraction LLM from properties and the {@code extract_dnd_propositions}
     * Jinja template to guide structured extraction.
     */
    @Bean
    LlmPropositionExtractor llmPropositionExtractor(
            AiBuilder aiBuilder,
            PropositionRepository propositionRepository,
            DiceAnchorsProperties properties) {
        var ai = aiBuilder.ai();
        return LlmPropositionExtractor
                .withLlm(properties.memory().extractionLlm())
                .withAi(ai)
                .withPropositionRepository(propositionRepository)
                .withSchemaAdherence(SchemaAdherence.DEFAULT)
                .withTemplate("dice/extract_dnd_propositions");
    }

    /**
     * LLM-based proposition reviser — deduplicates and refines extracted propositions
     * against existing propositions in the store before persisting.
     */
    @Bean
    PropositionReviser propositionReviser(
            AiBuilder aiBuilder,
            DiceAnchorsProperties properties) {
        var ai = aiBuilder.ai();
        return LlmPropositionReviser
                .withLlm(properties.memory().extractionLlm())
                .withAi(ai);
    }

    /**
     * Full DICE proposition extraction pipeline: extract, revise, persist.
     */
    @Bean
    PropositionPipeline propositionPipeline(
            LlmPropositionExtractor propositionExtractor,
            PropositionReviser propositionReviser,
            PropositionRepository propositionRepository) {
        logger.info("Building D&D proposition extraction pipeline");
        return PropositionPipeline
                .withExtractor(propositionExtractor)
                .withRevision(propositionReviser, propositionRepository);
    }
}
