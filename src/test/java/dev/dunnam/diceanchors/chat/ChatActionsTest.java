package dev.dunnam.diceanchors.chat;

import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.agent.core.Blackboard;
import com.embabel.agent.core.ProcessContext;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.Conversation;
import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.CompliancePolicyMode;
import dev.dunnam.diceanchors.anchor.ConflictStrategy;
import dev.dunnam.diceanchors.anchor.DedupStrategy;
import dev.dunnam.diceanchors.sim.engine.RunHistoryStoreType;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.assembly.CharHeuristicTokenCounter;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.persistence.PropositionNode;
import com.embabel.dice.proposition.PropositionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ChatActions")
@ExtendWith(MockitoExtension.class)
class ChatActionsTest {

    @Mock
    private AnchorEngine anchorEngine;

    @Mock
    private AnchorRepository anchorRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ActionContext actionContext;

    @Mock
    private Ai ai;

    @Mock
    private PromptRunner promptRunner;

    @Mock
    private PromptRunner.Rendering rendering;

    @Mock
    private ProcessContext processContext;

    @Mock
    private Blackboard blackboard;

    @Mock
    private ProcessOptions processOptions;

    @Mock
    private Conversation triggeredConversation;

    @Mock
    private Conversation persistedConversation;

    @Mock
    private ChatContextInitializer chatContextInitializer;

    @Test
    @DisplayName("publishes extraction event with persisted blackboard conversation")
    void publishEvent_usesPersistedConversationFromBlackboard() {
        var properties = properties();
        var assistantMessage = new AssistantMessage("ok");
        var actions = new ChatActions(anchorEngine, anchorRepository, eventPublisher, properties,
                dev.dunnam.diceanchors.anchor.CompliancePolicy.tiered(), new CharHeuristicTokenCounter(), null, chatContextInitializer);

        when(anchorEngine.inject("chat")).thenReturn(List.of());
        when(actionContext.ai()).thenReturn(ai);
        when(ai.withDefaultLlm()).thenReturn(promptRunner);
        when(promptRunner.withId("dice_anchors_response")).thenReturn(promptRunner);
        when(promptRunner.withToolObjects(org.mockito.ArgumentMatchers.<Object[]>any())).thenReturn(promptRunner);
        when(promptRunner.rendering("dice-anchors")).thenReturn(rendering);
        when(triggeredConversation.last(properties.memory().windowSize())).thenReturn(triggeredConversation);
        when(rendering.respondWithSystemPrompt(eq(triggeredConversation), anyMap())).thenReturn(assistantMessage);
        when(actionContext.getProcessContext()).thenReturn(processContext);
        when(processContext.getProcessOptions()).thenReturn(processOptions);
        when(processOptions.getContextIdString()).thenReturn("chat");
        when(processContext.getBlackboard()).thenReturn(blackboard);
        when(blackboard.objectsOfType(Conversation.class)).thenReturn(List.of(persistedConversation));

        actions.respond(triggeredConversation, actionContext);

        var eventCaptor = ArgumentCaptor.forClass(ConversationAnalysisRequestEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getConversation()).isSameAs(persistedConversation);
    }

    @Test
    @DisplayName("falls back to triggered conversation when blackboard has none")
    void publishEvent_fallsBackToTriggeredConversation() {
        var properties = properties();
        var assistantMessage = new AssistantMessage("ok");
        var actions = new ChatActions(anchorEngine, anchorRepository, eventPublisher, properties,
                dev.dunnam.diceanchors.anchor.CompliancePolicy.tiered(), new CharHeuristicTokenCounter(), null, chatContextInitializer);

        when(anchorEngine.inject("chat")).thenReturn(List.of());
        when(actionContext.ai()).thenReturn(ai);
        when(ai.withDefaultLlm()).thenReturn(promptRunner);
        when(promptRunner.withId("dice_anchors_response")).thenReturn(promptRunner);
        when(promptRunner.withToolObjects(org.mockito.ArgumentMatchers.<Object[]>any())).thenReturn(promptRunner);
        when(promptRunner.rendering("dice-anchors")).thenReturn(rendering);
        when(triggeredConversation.last(properties.memory().windowSize())).thenReturn(triggeredConversation);
        when(rendering.respondWithSystemPrompt(eq(triggeredConversation), anyMap())).thenReturn(assistantMessage);
        when(actionContext.getProcessContext()).thenReturn(processContext);
        when(processContext.getProcessOptions()).thenReturn(processOptions);
        when(processOptions.getContextIdString()).thenReturn("chat");
        when(processContext.getBlackboard()).thenReturn(blackboard);
        when(blackboard.objectsOfType(Conversation.class)).thenReturn(List.of());

        actions.respond(triggeredConversation, actionContext);

        var eventCaptor = ArgumentCaptor.forClass(ConversationAnalysisRequestEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getConversation()).isSameAs(triggeredConversation);
    }

    @Test
    @DisplayName("includes working proposition block in prompt variables")
    @SuppressWarnings("unchecked")
    void respond_includesWorkingPropositionBlock() {
        var properties = properties();
        var assistantMessage = new AssistantMessage("ok");
        var actions = new ChatActions(anchorEngine, anchorRepository, eventPublisher, properties,
                dev.dunnam.diceanchors.anchor.CompliancePolicy.tiered(), new CharHeuristicTokenCounter(), null, chatContextInitializer);

        when(anchorEngine.inject("chat")).thenReturn(List.of());
        when(anchorRepository.findActiveUnanchoredPropositions("chat", properties.anchor().budget()))
                .thenReturn(List.of(new PropositionNode(
                        "p1",
                        "chat",
                        "The moonpetal cure only grows near clean ley lines",
                        0.83,
                        0.0,
                        null,
                        List.of(),
                        java.time.Instant.now(),
                        java.time.Instant.now(),
                        PropositionStatus.ACTIVE,
                        null,
                        List.of()
                )));
        when(actionContext.ai()).thenReturn(ai);
        when(ai.withDefaultLlm()).thenReturn(promptRunner);
        when(promptRunner.withId("dice_anchors_response")).thenReturn(promptRunner);
        when(promptRunner.withToolObjects(org.mockito.ArgumentMatchers.<Object[]>any())).thenReturn(promptRunner);
        when(promptRunner.rendering("dice-anchors")).thenReturn(rendering);
        when(triggeredConversation.last(properties.memory().windowSize())).thenReturn(triggeredConversation);

        var varsCaptor = ArgumentCaptor.forClass(Map.class);
        when(rendering.respondWithSystemPrompt(eq(triggeredConversation), varsCaptor.capture())).thenReturn(assistantMessage);
        when(actionContext.getProcessContext()).thenReturn(processContext);
        when(processContext.getProcessOptions()).thenReturn(processOptions);
        when(processOptions.getContextIdString()).thenReturn("chat");
        when(processContext.getBlackboard()).thenReturn(blackboard);
        when(blackboard.objectsOfType(Conversation.class)).thenReturn(List.of(triggeredConversation));

        actions.respond(triggeredConversation, actionContext);

        var vars = (Map<String, Object>) varsCaptor.getValue();
        assertThat(vars.get("proposition_block"))
                .asString()
                .contains("Working Propositions")
                .contains("moonpetal cure");
    }

    private static DiceAnchorsProperties properties() {
        return new DiceAnchorsProperties(
                new DiceAnchorsProperties.AnchorConfig(20, 500, 100, 900, true, 0.65, DedupStrategy.FAST_THEN_LLM, CompliancePolicyMode.TIERED, true, true, true, 0.6, 400, 200, null, null, null, null),
                new DiceAnchorsProperties.ChatConfig("dm", 200, null),
                new DiceAnchorsProperties.MemoryConfig(true, null, null, "text-embedding-3-small", 20, 5, 2),
                new DiceAnchorsProperties.PersistenceConfig(false),
                new DiceAnchorsProperties.SimConfig("gpt-4.1-mini", 30, 30, 10, true, 4),
                new DiceAnchorsProperties.ConflictDetectionConfig(ConflictStrategy.LLM, "gpt-4o-nano"),
                new DiceAnchorsProperties.RunHistoryConfig(RunHistoryStoreType.MEMORY),
                new DiceAnchorsProperties.AssemblyConfig(0),
                null, null
        );
    }
}
