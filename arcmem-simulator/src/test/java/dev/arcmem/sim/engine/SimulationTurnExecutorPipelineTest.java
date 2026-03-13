package dev.arcmem.simulator.engine;
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

import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.core.persistence.PropositionNode;
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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimulationTurnExecutor pipeline")
class SimulationTurnExecutorPipelineTest {

    @Mock private ChatModelHolder chatModel;
    @Mock private dev.arcmem.core.memory.engine.ArcMemEngine arcMemEngine;
    @Mock private dev.arcmem.core.persistence.MemoryUnitRepository contextUnitRepository;
    @Mock private SimulationExtractionService extractionService;
    @Mock private ComplianceEnforcer complianceEnforcer;
    @Mock private MemoryPressureGauge pressureGauge;

    private SimulationTurnExecutor buildExecutor(ArcMemProperties properties) {
        var injectionEnforcer = new LoggingPromptInjectionEnforcer();
        var maintenanceStrategy = new ReactiveMaintenanceStrategy(
                DecayPolicy.exponential(1000.0), ReinforcementPolicy.threshold());
        var turnServices = new SimulationTurnServices(
                extractionService, maintenanceStrategy, complianceEnforcer, pressureGauge, injectionEnforcer);
        return new SimulationTurnExecutor(
                chatModel,
                arcMemEngine,
                contextUnitRepository,
                properties,
                CompliancePolicy.flat(),
                text -> Math.max(1, text.length() / 4),
                null,
                null,
                turnServices);
    }

    private ArcMemProperties defaultProperties() {
        return new ArcMemProperties(
                new ArcMemProperties.UnitConfig(20, 500, 100, 900, true, 0.65, DedupStrategy.FAST_THEN_LLM, CompliancePolicyMode.TIERED, true, true, true, 0.6, 400, 200, null, null, null, null, null),
                null, null, null, null, null, null,
                new ArcMemProperties.AssemblyConfig(0, false, EnforcementStrategy.PROMPT_ONLY), null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("executeTurnFull refreshes memory units after extraction and reinforces injected memory units")
    void executeTurnFullRefreshesUnitsAfterExtractionAndReinforcesInjectedUnits() {
        var properties = defaultProperties();
        var executor = buildExecutor(properties);

        when(complianceEnforcer.enforce(any())).thenReturn(ComplianceResult.compliant(Duration.ZERO));

        var dmResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("DM response text"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(dmResponse);

        var injectedUnit = MemoryUnit.withoutTrust("a1", "Seed fact", 500, Authority.PROVISIONAL, false, 0.9, 0);
        var reinforcedUnit = MemoryUnit.withoutTrust("a1", "Seed fact", 550, Authority.PROVISIONAL, false, 0.9, 1);
        var promotedUnit = MemoryUnit.withoutTrust("a2", "Newly promoted fact", 500, Authority.PROVISIONAL, false, 0.85, 0);

        when(arcMemEngine.inject("sim-ctx")).thenReturn(
                List.of(injectedUnit),
                List.of(reinforcedUnit, promotedUnit));
        when(extractionService.extract("sim-ctx", "DM response text"))
                .thenReturn(new ExtractionResult(2, 1, 0, List.of("Newly promoted fact")));

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
                Map.of("a1", injectedUnit),
                null,
                null,
                true,
                null,
                new java.util.HashMap<>());

        verify(arcMemEngine).reinforce("a1", true, true);

        assertThat(result.turn().contextTrace().propositionsExtracted()).isEqualTo(2);
        assertThat(result.turn().contextTrace().propositionsPromoted()).isEqualTo(1);
        assertThat(result.turn().contextTrace().injectedUnits())
                .extracting(MemoryUnit::id)
                .containsExactly("a1", "a2");

        assertThat(result.currentUnitState()).containsOnlyKeys("a1", "a2");
        assertThat(result.turn().unitEvents())
                .extracting(SimulationTurn.MemoryUnitEvent::eventType)
                .contains("REINFORCED", "CREATED");
    }

    @Test
    @DisplayName("executeTurn injects working propositions into the system prompt")
    void executeTurnInjectsWorkingPropositionsIntoSystemPrompt() {
        var properties = defaultProperties();
        var executor = buildExecutor(properties);

        when(complianceEnforcer.enforce(any())).thenReturn(ComplianceResult.compliant(Duration.ZERO));

        var dmResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("DM response text"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(dmResponse);
        when(arcMemEngine.inject("sim-ctx")).thenReturn(List.of());
        when(contextUnitRepository.findActiveUnpromotedPropositions("sim-ctx", properties.unit().budget()))
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
    @DisplayName("executeTurn renders memory unit tiers without duplicate compliance headers or blank rows")
    void executeTurnRendersUnitsWithoutDuplicateHeaders() {
        var properties = defaultProperties();
        var executor = buildExecutor(properties);

        when(complianceEnforcer.enforce(any())).thenReturn(ComplianceResult.compliant(Duration.ZERO));

        var dmResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("DM response text"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(dmResponse);
        when(arcMemEngine.inject("sim-ctx")).thenReturn(List.of(
                MemoryUnit.withoutTrust("a1", "The East Gate is breached", 650, Authority.RELIABLE, false, 0.88, 0)
        ));
        when(contextUnitRepository.findActiveUnpromotedPropositions("sim-ctx", properties.unit().budget()))
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
                .contains("(activation score: 650)")
                .doesNotContain("1.  (activation score: )");
    }

    @Test
    @DisplayName("executeTurnFull applies dormancy decay and emits DECAYED event")
    void executeTurnFullAppliesDormancyDecayAndEmitsDecayedEvent() {
        var properties = defaultProperties();
        var executor = buildExecutor(properties);

        var dmResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("DM response text"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(dmResponse);

        var previousUnit = MemoryUnit.withoutTrust("a1", "Dormant fact", 500, Authority.PROVISIONAL, false, 0.9, 0);
        var decayedUnit = MemoryUnit.withoutTrust("a1", "Dormant fact", 400, Authority.PROVISIONAL, false, 0.9, 0);

        when(arcMemEngine.inject("sim-ctx")).thenReturn(
                List.of(previousUnit),
                List.of(previousUnit),
                List.of(decayedUnit));

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
                Map.of("a1", previousUnit),
                null,
                null,
                false,
                new SimulationScenario.DormancyConfig(0.2, 0.1, 1),
                new java.util.HashMap<>());

        verify(arcMemEngine, never()).reinforce(any(), anyBoolean(), anyBoolean());
        verify(arcMemEngine).applyDecay(eq("a1"), eq(400));
        assertThat(result.turn().unitEvents())
                .extracting(SimulationTurn.MemoryUnitEvent::eventType)
                .contains("DECAYED");
    }
}
