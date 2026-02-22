package dev.dunnam.diceanchors.sim.engine;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.CompliancePolicy;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.persistence.PropositionNode;
import com.embabel.dice.proposition.PropositionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimulationTurnExecutor pipeline")
class SimulationTurnExecutorPipelineTest {

    @Mock private ChatModelHolder chatModel;
    @Mock private dev.dunnam.diceanchors.anchor.AnchorEngine anchorEngine;
    @Mock private dev.dunnam.diceanchors.persistence.AnchorRepository anchorRepository;
    @Mock private SimulationExtractionService extractionService;

    @Test
    @DisplayName("executeTurnFull refreshes anchors after extraction and reinforces injected anchors")
    void executeTurnFullRefreshesAnchorsAfterExtractionAndReinforcesInjectedAnchors() {
        var properties = new DiceAnchorsProperties(
                new DiceAnchorsProperties.AnchorConfig(20, 500, 100, 900, true, 0.65, "FAST_THEN_LLM", "TIERED", true, true, true, 0.6, 400, 200, null),
                null, null, null, null, null, null,
                new DiceAnchorsProperties.AssemblyConfig(0), null);
        var executor = new SimulationTurnExecutor(
                chatModel,
                anchorEngine,
                anchorRepository,
                properties,
                CompliancePolicy.flat(),
                text -> Math.max(1, text.length() / 4),
                extractionService);

        var dmResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("DM response text"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(dmResponse);

        var injectedAnchor = Anchor.withoutTrust("a1", "Seed fact", 500, Authority.PROVISIONAL, false, 0.9, 0);
        var reinforcedAnchor = Anchor.withoutTrust("a1", "Seed fact", 550, Authority.PROVISIONAL, false, 0.9, 1);
        var promotedAnchor = Anchor.withoutTrust("a2", "Newly promoted fact", 500, Authority.PROVISIONAL, false, 0.85, 0);

        when(anchorEngine.inject("sim-ctx")).thenReturn(
                List.of(injectedAnchor),
                List.of(reinforcedAnchor, promotedAnchor));
        when(extractionService.extract("sim-ctx", "DM response text"))
                .thenReturn(new ExtractionResult(2, 1, List.of("Newly promoted fact")));

        var result = executor.executeTurnFull(
                "sim-ctx",
                1,
                "Player asks for lore",
                TurnType.ESTABLISH,
                null,
                "campaign setting",
                true,
                0,
                List.of(),
                List.of(),
                Map.of("a1", injectedAnchor),
                null,
                null,
                true,
                null,
                new java.util.HashMap<>());

        verify(anchorEngine).reinforce("a1");

        assertThat(result.turn().contextTrace().propositionsExtracted()).isEqualTo(2);
        assertThat(result.turn().contextTrace().propositionsPromoted()).isEqualTo(1);
        assertThat(result.turn().contextTrace().injectedAnchors())
                .extracting(Anchor::id)
                .containsExactly("a1", "a2");

        assertThat(result.currentAnchorState()).containsOnlyKeys("a1", "a2");
        assertThat(result.turn().anchorEvents())
                .extracting(SimulationTurn.AnchorEvent::eventType)
                .contains("REINFORCED", "CREATED");
    }

    @Test
    @DisplayName("executeTurn injects working propositions into the system prompt")
    void executeTurnInjectsWorkingPropositionsIntoSystemPrompt() {
        var properties = new DiceAnchorsProperties(
                new DiceAnchorsProperties.AnchorConfig(20, 500, 100, 900, true, 0.65, "FAST_THEN_LLM", "TIERED", true, true, true, 0.6, 400, 200, null),
                null, null, null, null, null, null,
                new DiceAnchorsProperties.AssemblyConfig(0), null);
        var executor = new SimulationTurnExecutor(
                chatModel,
                anchorEngine,
                anchorRepository,
                properties,
                CompliancePolicy.flat(),
                text -> Math.max(1, text.length() / 4),
                extractionService);

        var dmResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("DM response text"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(dmResponse);
        when(anchorEngine.inject("sim-ctx")).thenReturn(List.of());
        when(anchorRepository.findActiveUnanchoredPropositions("sim-ctx", properties.anchor().budget()))
                .thenReturn(List.of(new PropositionNode(
                        "p1",
                        "sim-ctx",
                        "Moonpetal petals heal shadow blight near clean ley lines",
                        0.81,
                        0.0,
                        null,
                        List.of(),
                        java.time.Instant.now(),
                        java.time.Instant.now(),
                        PropositionStatus.ACTIVE,
                        null,
                        List.of()
                )));

        executor.executeTurn(
                "sim-ctx",
                1,
                "Player asks for lore",
                TurnType.ESTABLISH,
                null,
                "campaign setting",
                true,
                0,
                List.of(),
                List.of());

        var promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        var systemPrompt = promptCaptor.getValue().getInstructions().stream()
                                       .filter(msg -> msg instanceof org.springframework.ai.chat.messages.SystemMessage)
                                       .findFirst()
                                       .orElseThrow()
                                       .getText();

        assertThat(systemPrompt)
                .contains("Working Propositions")
                .contains("Moonpetal petals heal shadow blight");
    }

    @Test
    @DisplayName("executeTurn renders anchor tiers without duplicate compliance headers or blank rows")
    void executeTurnRendersAnchorsWithoutDuplicateHeaders() {
        var properties = new DiceAnchorsProperties(
                new DiceAnchorsProperties.AnchorConfig(20, 500, 100, 900, true, 0.65, "FAST_THEN_LLM", "TIERED", true, true, true, 0.6, 400, 200, null),
                null, null, null, null, null, null,
                new DiceAnchorsProperties.AssemblyConfig(0), null);
        var executor = new SimulationTurnExecutor(
                chatModel,
                anchorEngine,
                anchorRepository,
                properties,
                CompliancePolicy.flat(),
                text -> Math.max(1, text.length() / 4),
                extractionService);

        var dmResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("DM response text"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(dmResponse);
        when(anchorEngine.inject("sim-ctx")).thenReturn(List.of(
                Anchor.withoutTrust("a1", "The East Gate is breached", 650, Authority.RELIABLE, false, 0.88, 0)
        ));
        when(anchorRepository.findActiveUnanchoredPropositions("sim-ctx", properties.anchor().budget()))
                .thenReturn(List.of());

        executor.executeTurn(
                "sim-ctx",
                1,
                "Player asks for lore",
                TurnType.ESTABLISH,
                null,
                "campaign setting",
                true,
                0,
                List.of(),
                List.of());

        var promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        var systemPrompt = promptCaptor.getValue().getInstructions().stream()
                                       .filter(msg -> msg instanceof org.springframework.ai.chat.messages.SystemMessage)
                                       .findFirst()
                                       .orElseThrow()
                                       .getText();

        assertThat(systemPrompt)
                .containsOnlyOnce("[Established Facts - Compliance Framework]")
                .contains("The East Gate is breached")
                .contains("(rank: 650)")
                .doesNotContain("1.  (rank: )");
    }

    @Test
    @DisplayName("executeTurnFull applies dormancy decay and emits DECAYED event")
    void executeTurnFullAppliesDormancyDecayAndEmitsDecayedEvent() {
        var properties = new DiceAnchorsProperties(
                new DiceAnchorsProperties.AnchorConfig(20, 500, 100, 900, true, 0.65, "FAST_THEN_LLM", "TIERED", true, true, true, 0.6, 400, 200, null),
                null, null, null, null, null, null,
                new DiceAnchorsProperties.AssemblyConfig(0), null);
        var executor = new SimulationTurnExecutor(
                chatModel,
                anchorEngine,
                anchorRepository,
                properties,
                CompliancePolicy.flat(),
                text -> Math.max(1, text.length() / 4),
                extractionService);

        var dmResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("DM response text"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(dmResponse);

        var previousAnchor = Anchor.withoutTrust("a1", "Dormant fact", 500, Authority.PROVISIONAL, false, 0.9, 0);
        var decayedAnchor = Anchor.withoutTrust("a1", "Dormant fact", 400, Authority.PROVISIONAL, false, 0.9, 0);

        when(anchorEngine.inject("sim-ctx")).thenReturn(
                List.of(previousAnchor),
                List.of(previousAnchor),
                List.of(decayedAnchor));

        var result = executor.executeTurnFull(
                "sim-ctx",
                2,
                "Player asks unrelated question",
                TurnType.ESTABLISH,
                null,
                "campaign setting",
                false,
                0,
                List.of(),
                List.of(),
                Map.of("a1", previousAnchor),
                null,
                null,
                false,
                new SimulationScenario.DormancyConfig(0.2, 0.1, 1),
                new java.util.HashMap<>());

        verify(anchorEngine, never()).reinforce(any());
        verify(anchorEngine).applyDecay(eq("a1"), eq(400));
        assertThat(result.turn().anchorEvents())
                .extracting(SimulationTurn.AnchorEvent::eventType)
                .contains("DECAYED");
    }
}
