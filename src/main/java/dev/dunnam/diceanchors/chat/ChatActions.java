package dev.dunnam.diceanchors.chat;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.EmbabelComponent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.chat.Conversation;
import com.embabel.chat.UserMessage;
import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.CompliancePolicy;
import dev.dunnam.diceanchors.assembly.AnchorsLlmReference;
import dev.dunnam.diceanchors.assembly.PropositionsLlmReference;
import dev.dunnam.diceanchors.assembly.TokenCounter;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Embabel chat component that assembles anchor context into each LLM response.
 * <p>
 * Follows the Impromptu {@code ChatActions} pattern:
 * an {@code @EmbabelComponent} record with an {@code @Action} triggered by
 * each {@code UserMessage}. The top-ranked active anchors are loaded from
 * {@link AnchorEngine} and passed to the Jinja template as the {@code anchors}
 * variable, where {@code anchor-context.jinja} renders the ESTABLISHED FACTS block.
 * <p>
 * DICE Memory ({@code Memory.forContext()}) is deferred: the API expects
 * {@code LlmReference} from {@code com.embabel.agent.api.reference}, which diverged
 * from {@code com.embabel.common.ai.model.LlmReference} in Embabel 0.3.5-SNAPSHOT.
 * Async extraction still runs via {@link ConversationPropositionExtraction}.
 */
@EmbabelComponent
public record ChatActions(
        AnchorEngine anchorEngine,
        AnchorRepository anchorRepository,
        ApplicationEventPublisher eventPublisher,
        DiceAnchorsProperties properties,
        CompliancePolicy compliancePolicy,
        TokenCounter tokenCounter
) {

    private static final Logger logger = LoggerFactory.getLogger(ChatActions.class);
    private static final String DEFAULT_CONTEXT = "chat";

    /**
     * Invoked for each user message in the conversation.
     * <p>
     * Loads active anchors for the current context, assembles the system prompt
     * via Jinja template rendering (the {@code anchors} variable feeds
     * {@code anchor-context.jinja}), and publishes a
     * {@link ConversationAnalysisRequestEvent} for async DICE extraction.
     */
    @Action(canRerun = true, trigger = UserMessage.class)
    void respond(@NonNull Conversation conversation, ActionContext context) {
        var contextId = DEFAULT_CONTEXT;
        var window = properties.memory().windowSize();

        var anchorRef = new AnchorsLlmReference(
                anchorEngine,
                contextId,
                properties.anchor().budget(),
                compliancePolicy,
                properties.assembly().promptTokenBudget(),
                tokenCounter);
        var propositionRef = new PropositionsLlmReference(
                anchorRepository,
                contextId,
                properties.anchor().budget());

        // Load the top-ranked active anchors for context injection.
        var anchors = anchorRef.getAnchors();
        var propositionBlock = propositionRef.getContent();

        // Convert Anchor records to Maps for Jinjava compatibility.
        // Jinjava expects JavaBean-style getters (getText()) but Java records
        // use accessor methods (text()), so record properties are unresolvable.
        var anchorMaps = anchors.stream()
                .map(a -> Map.<String, Object>of(
                        "text", a.text(),
                        "rank", a.rank(),
                        "authority", a.authority().name()
                ))
                .toList();

        // Build tiered anchor groups for compliance-aware prompt rendering.
        var tiered = properties.anchor().compliancePolicy().equalsIgnoreCase("TIERED");
        var templateVars = new HashMap<String, Object>();
        templateVars.put("properties", properties);
        templateVars.put("anchors", anchorMaps);
        templateVars.put("proposition_block", propositionBlock);
        templateVars.put("persona", properties.chat().persona());
        templateVars.put("tiered", tiered);

        if (tiered) {
            var grouped = anchors.stream()
                    .collect(Collectors.groupingBy(Anchor::authority));
            for (var authority : Authority.values()) {
                var key = authority.name().toLowerCase() + "_anchors";
                var group = grouped.getOrDefault(authority, List.of()).stream()
                        .map(a -> Map.<String, Object>of("text", a.text(), "rank", a.rank()))
                        .toList();
                templateVars.put(key, group);
            }
        }

        logger.debug("Responding with {} active anchors and {} working propositions for context {} (tiered={})",
                anchors.size(), propositionRef.getPropositions().size(), contextId, tiered);

        var tools = new AnchorTools(anchorEngine, anchorRepository, contextId);
        var assistantMessage = context.ai()
                .withDefaultLlm()
                .withId("dice_anchors_response")
                .withToolObjects(tools)
                .rendering("dice-anchors")
                .respondWithSystemPrompt(
                        conversation.last(window),
                        templateVars
                );

        context.sendAndSave(assistantMessage);

        // Reinforce all active anchors after a successful response.
        // This increments reinforcement count, applies rank boost, and may upgrade authority.
        if (!anchors.isEmpty()) {
            logger.debug("Reinforcing {} active anchors for context {}", anchors.size(), contextId);
            for (var anchor : anchors) {
                anchorEngine.reinforce(anchor.id());
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
}
