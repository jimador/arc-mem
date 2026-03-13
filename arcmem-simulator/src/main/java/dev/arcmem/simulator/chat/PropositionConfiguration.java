package dev.arcmem.simulator.chat;
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
import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.simulator.domain.dnd.Character;
import dev.arcmem.simulator.domain.dnd.Creature;
import dev.arcmem.simulator.domain.dnd.DndItem;
import dev.arcmem.simulator.domain.dnd.DndLocation;
import dev.arcmem.simulator.domain.dnd.Faction;
import dev.arcmem.simulator.domain.dnd.StoryEvent;
import dev.arcmem.core.persistence.ArcMemChunkHistoryStore;
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
 * patterns, adapted for the arc-mem demo:
 * <ul>
 *   <li>D&D entity types replace music domain types</li>
 *   <li>{@code MemoryUnitRepository} serves as the {@link PropositionRepository} (Neo4j-backed)</li>
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
                "arc-mem",
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
     * {@link ArcMemChunkHistoryStore} wrapper for injection into
     * the incremental analysis pipeline.
     */
    @Bean
    ChunkHistoryStore chunkHistoryStore(ArcMemChunkHistoryStore store) {
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
            ArcMemProperties properties) {
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
            ArcMemProperties properties) {
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
