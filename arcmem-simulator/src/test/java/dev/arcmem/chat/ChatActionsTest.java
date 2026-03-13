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

import dev.arcmem.core.spi.llm.*;
import dev.arcmem.simulator.engine.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.agent.core.Blackboard;
import com.embabel.agent.core.ProcessContext;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.Conversation;
import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.simulator.config.ArcMemSimulatorProperties;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.persistence.PropositionNode;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ChatActions")
@ExtendWith(MockitoExtension.class)
class ChatActionsTest {

    @Mock
    private ArcMemEngine arcMemEngine;

    @Mock
    private MemoryUnitRepository contextUnitRepository;

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
        var actions = new ChatActions(arcMemEngine, contextUnitRepository, eventPublisher, properties,
                simulatorProperties(), dev.arcmem.core.memory.canon.CompliancePolicy.tiered(), new CharHeuristicTokenCounter(), null, chatContextInitializer, Optional.empty());

        when(arcMemEngine.inject("chat")).thenReturn(List.of());
        when(actionContext.ai()).thenReturn(ai);
        when(ai.withDefaultLlm()).thenReturn(promptRunner);
        when(promptRunner.withId("arc_mem_response")).thenReturn(promptRunner);
        when(promptRunner.withToolObjects(org.mockito.ArgumentMatchers.<Object>any(), org.mockito.ArgumentMatchers.<Object>any())).thenReturn(promptRunner);
        when(promptRunner.rendering("arc-mem")).thenReturn(rendering);
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
        var actions = new ChatActions(arcMemEngine, contextUnitRepository, eventPublisher, properties,
                simulatorProperties(), dev.arcmem.core.memory.canon.CompliancePolicy.tiered(), new CharHeuristicTokenCounter(), null, chatContextInitializer, Optional.empty());

        when(arcMemEngine.inject("chat")).thenReturn(List.of());
        when(actionContext.ai()).thenReturn(ai);
        when(ai.withDefaultLlm()).thenReturn(promptRunner);
        when(promptRunner.withId("arc_mem_response")).thenReturn(promptRunner);
        when(promptRunner.withToolObjects(org.mockito.ArgumentMatchers.<Object>any(), org.mockito.ArgumentMatchers.<Object>any())).thenReturn(promptRunner);
        when(promptRunner.rendering("arc-mem")).thenReturn(rendering);
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
        var actions = new ChatActions(arcMemEngine, contextUnitRepository, eventPublisher, properties,
                simulatorProperties(), dev.arcmem.core.memory.canon.CompliancePolicy.tiered(), new CharHeuristicTokenCounter(), null, chatContextInitializer, Optional.empty());

        when(arcMemEngine.inject("chat")).thenReturn(List.of());
        when(contextUnitRepository.findActiveUnpromotedPropositions("chat", properties.unit().budget()))
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
        when(promptRunner.withId("arc_mem_response")).thenReturn(promptRunner);
        when(promptRunner.withToolObjects(org.mockito.ArgumentMatchers.<Object>any(), org.mockito.ArgumentMatchers.<Object>any())).thenReturn(promptRunner);
        when(promptRunner.rendering("arc-mem")).thenReturn(rendering);
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

    private static ArcMemProperties properties() {
        return new ArcMemProperties(
                new ArcMemProperties.UnitConfig(20, 500, 100, 900, true, 0.65, DedupStrategy.FAST_THEN_LLM, CompliancePolicyMode.TIERED, true, true, true, 0.6, 400, 200, null, null, null, null, null),
                new ArcMemProperties.MemoryConfig(true, null, null, "text-embedding-3-small", 20, 5, 2),
                new ArcMemProperties.PersistenceConfig(false),
                new ArcMemProperties.ConflictDetectionConfig(ConflictStrategy.LLM, "gpt-4o-nano"),
                new ArcMemProperties.AssemblyConfig(0, false, dev.arcmem.core.assembly.compliance.EnforcementStrategy.PROMPT_ONLY),
                null, null, null, null, null, null, null,
                new ArcMemProperties.LlmCallConfig(30, 10)
        );
    }

    private static ArcMemSimulatorProperties simulatorProperties() {
        return new ArcMemSimulatorProperties(
                new ArcMemSimulatorProperties.ChatConfig("dm", 200, null),
                new ArcMemSimulatorProperties.SimConfig("gpt-4.1-mini", 30, true, 4),
                null);
    }
}
