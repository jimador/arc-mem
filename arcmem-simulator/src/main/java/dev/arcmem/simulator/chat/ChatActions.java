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

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.EmbabelComponent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.chat.Conversation;
import com.embabel.chat.UserMessage;
import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.simulator.config.ArcMemSimulatorProperties;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Embabel chat component that assembles unit context into each LLM response.
 * <p>
 * Follows the Impromptu {@code ChatActions} pattern:
 * an {@code @EmbabelComponent} record with an {@code @Action} triggered by
 * each {@code UserMessage}. The top-ranked active units are loaded from
 * {@link ArcMemEngine} and passed to the Jinja template as the {@code units}
 * variable, where {@code unit-context.jinja} renders the ESTABLISHED FACTS block.
 * <p>
 * DICE Memory ({@code Memory.forContext()}) is deferred: the API expects
 * {@code LlmReference} from {@code com.embabel.agent.api.reference}, which diverged
 * from {@code com.embabel.common.ai.model.LlmReference} in Embabel 0.3.5-SNAPSHOT.
 * Async extraction still runs via {@link ConversationPropositionExtraction}.
 */
@EmbabelComponent
public record ChatActions(
        ArcMemEngine arcMemEngine,
        MemoryUnitRepository contextUnitRepository,
        ApplicationEventPublisher eventPublisher,
        ArcMemProperties properties,
        ArcMemSimulatorProperties simulatorProperties,
        CompliancePolicy compliancePolicy,
        TokenCounter tokenCounter,
        RelevanceScorer relevanceScorer,
        ChatContextInitializer chatContextInitializer
) {

    private static final Logger logger = LoggerFactory.getLogger(ChatActions.class);
    private static final String FALLBACK_CONTEXT = "chat";

    /**
     * Invoked for each user message in the conversation.
     * <p>
     * Loads active units for the current context, assembles the system prompt
     * via Jinja template rendering (the {@code units} variable feeds
     * {@code unit-context.jinja}), and publishes a
     * {@link ConversationAnalysisRequestEvent} for async DICE extraction.
     */
    @Action(canRerun = true, trigger = UserMessage.class)
    void respond(@NonNull Conversation conversation, ActionContext context) {
        var contextId = resolveContextId(context);
        chatContextInitializer.initializeContext(contextId);
        var window = properties.memory().windowSize();

        var unitRef = new ArcMemLlmReference(
                arcMemEngine,
                contextId,
                properties.unit().budget(),
                compliancePolicy,
                properties.assembly().promptTokenBudget(),
                tokenCounter,
                null,
                properties.retrieval(),
                relevanceScorer);
        var propositionRef = new PropositionsLlmReference(
                contextUnitRepository,
                contextId,
                properties.unit().budget());

        // Load the top-ranked active units for context injection.
        var units = unitRef.getUnits();
        var propositionBlock = propositionRef.getContent();

        // Convert MemoryUnit records to Maps for Jinjava compatibility.
        // Jinjava expects JavaBean-style getters (getText()) but Java records
        // use accessor methods (text()), so record properties are unresolvable.
        var unitMaps = units.stream()
                .map(a -> Map.<String, Object>of(
                        "text", a.text(),
                        "rank", a.rank(),
                        "authority", a.authority().name()
                ))
                .toList();

        // Build tiered unit groups for compliance-aware prompt rendering.
        var tiered = properties.unit().compliancePolicy() == CompliancePolicyMode.TIERED;
        var templateVars = new HashMap<String, Object>();
        templateVars.put("properties", properties);
        templateVars.put("units", unitMaps);
        templateVars.put("proposition_block", propositionBlock);
        templateVars.put("persona", simulatorProperties.chat().persona());
        templateVars.put("tiered", tiered);

        if (tiered) {
            var grouped = units.stream()
                    .collect(Collectors.groupingBy(MemoryUnit::authority));
            for (var authority : Authority.values()) {
                var key = authority.name().toLowerCase() + "_units";
                var group = grouped.getOrDefault(authority, List.of()).stream()
                        .map(a -> Map.<String, Object>of("text", a.text(), "rank", a.rank()))
                        .toList();
                templateVars.put(key, group);
            }
        }

        logger.debug("Responding with {} active units and {} working propositions for context {} (tiered={})",
                units.size(), propositionRef.getPropositions().size(), contextId, tiered);

        var retrievalConfig = properties.retrieval();
        var retrievalMode = retrievalConfig != null ? retrievalConfig.mode() : RetrievalMode.BULK;
        var activeConfig = (retrievalMode == RetrievalMode.HYBRID || retrievalMode == RetrievalMode.TOOL)
                ? retrievalConfig : null;

        var queryTools = new MemoryUnitQueryTools(arcMemEngine, contextUnitRepository, relevanceScorer,
                contextId, activeConfig, new AtomicInteger(0));
        var mutationTools = new MemoryUnitMutationTools(arcMemEngine, contextUnitRepository, contextId);

        var assistantMessage = context.ai()
                .withDefaultLlm()
                .withId("arc_mem_response")
                .withToolObjects(queryTools, mutationTools)
                .rendering("arc-mem")
                .respondWithSystemPrompt(
                        conversation.last(window),
                        templateVars
                );

        context.sendAndSave(assistantMessage);

        // Reinforce all active units after a successful response.
        // This increments reinforcement count, applies rank boost, and may upgrade authority.
        if (!units.isEmpty()) {
            logger.debug("Reinforcing {} active units for context {}", units.size(), contextId);
            for (var unit : units) {
                arcMemEngine.reinforce(unit.id());
            }
        }

        // Trigger async DICE proposition extraction after the response is sent.
        // ConversationPropositionExtraction handles the event and runs the extraction pipeline.
        var persistedConversation = context.getProcessContext()
                .getBlackboard()
                .objectsOfType(Conversation.class)
                .stream()
                .findFirst()
                .orElse(conversation);
        eventPublisher.publishEvent(
                new ConversationAnalysisRequestEvent(this, contextId, persistedConversation));
        logger.debug("Published ConversationAnalysisRequestEvent for context {}", contextId);
    }

    private String resolveContextId(ActionContext context) {
        var processContextId = context.getProcessContext().getProcessOptions().getContextIdString();
        return processContextId != null ? processContextId : FALLBACK_CONTEXT;
    }
}
