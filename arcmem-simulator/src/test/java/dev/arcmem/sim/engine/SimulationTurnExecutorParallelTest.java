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
import dev.arcmem.simulator.evaluation.DriftReEvaluator;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.simulator.config.ArcMemSimulatorProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimulationTurnExecutor parallel pipeline")
class SimulationTurnExecutorParallelTest {

    @Mock private ChatModelHolder chatModel;
    @Mock private ArcMemEngine arcMemEngine;
    @Mock private MemoryUnitRepository contextUnitRepository;
    @Mock private SimulationExtractionService extractionService;
    @Mock private ComplianceEnforcer complianceEnforcer;
    @Mock private MemoryPressureGauge pressureGauge;

    private SimulationTurnExecutor executorWithFlag(boolean parallelPostResponse) {
        var properties = new ArcMemProperties(
                new ArcMemProperties.UnitConfig(20, 500, 100, 900, true, 0.65, DedupStrategy.FAST_THEN_LLM, CompliancePolicyMode.TIERED, true, true, true, 0.6, 400, 200, null, null, null, null, null),
                null, null, null,
                new ArcMemProperties.AssemblyConfig(0, false, EnforcementStrategy.PROMPT_ONLY),
                null, null, null, null, null, null,
                new ArcMemProperties.LlmCallConfig(30, 10));
        var simulatorProperties = new ArcMemSimulatorProperties(null,
                new ArcMemSimulatorProperties.SimConfig("gpt-4.1-mini", 30, parallelPostResponse, 4), null);
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
                simulatorProperties,
                CompliancePolicy.flat(),
                text -> Math.max(1, text.length() / 4),
                null,
                turnServices,
                new DriftReEvaluator(chatModel));
    }

    private static MemoryUnit unit(String id, String text) {
        return MemoryUnit.withoutTrust(id, text, 500, Authority.PROVISIONAL, false, 0.9, 0);
    }

    private void stubDmResponse(String text) {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
    }

    @Nested
    @DisplayName("parallelPostResponse=false uses sequential execution")
    class ParallelFlagFalse {

        @Test
        @DisplayName("sequential path: extractionService called after DM response")
        void parallelPostResponseFalseUsesSequentialExecution() {
            var executor = executorWithFlag(false);
            stubDmResponse("The dungeon stretches ahead.");
            var injectedUnit = unit("a1", "The dungeon is dark");

            when(complianceEnforcer.enforce(any())).thenReturn(ComplianceResult.compliant(Duration.ZERO));
            when(arcMemEngine.inject("ctx")).thenReturn(
                    List.of(injectedUnit),
                    List.of(injectedUnit));
            when(extractionService.extract("ctx", "The dungeon stretches ahead."))
                    .thenReturn(new ExtractionResult(1, 0, 0, List.of("The dungeon stretches ahead.")));

            var result = executor.executeTurnFull(
                    "ctx", 1, "What do I see?", TurnType.ESTABLISH, null,
                    "fantasy dungeon", true, 0, List.of(), List.of(),
                    Map.of(), null, null, true, null, new HashMap<>());

            verify(extractionService).extract("ctx", "The dungeon stretches ahead.");
            assertThat(result.turn().contextTrace().propositionsExtracted()).isEqualTo(1);
        }

        @Test
        @DisplayName("sequential path: extractionService NOT called when extraction disabled")
        void parallelPostResponseFalseExtractionDisabledSkipsExtraction() {
            var executor = executorWithFlag(false);
            stubDmResponse("You see a torch-lit corridor.");

            when(arcMemEngine.inject("ctx")).thenReturn(List.of(), List.of());

            executor.executeTurnFull(
                    "ctx", 1, "Look around.", TurnType.WARM_UP, null,
                    "fantasy dungeon", false, 0, List.of(), List.of(),
                    Map.of(), null, null, false, null, new HashMap<>());

            verify(extractionService, never()).extract(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("parallelPostResponse=true: ATTACK turn runs both branches")
    class AttackTurnParallel {

        @Test
        @DisplayName("ATTACK turn with extraction runs drift eval (Branch A) and extraction (Branch B)")
        void attackTurnWithExtractionRunsBothBranchesParallel() {
            var executor = executorWithFlag(true);
            when(chatModel.call(any(Prompt.class)))
                    .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("DM: The king still lives.")))))
                    .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(
                            "{\"verdicts\":[{\"fact_id\":\"f1\",\"verdict\":\"CONFIRMED\",\"severity\":\"NONE\",\"explanation\":\"confirmed\"}]}")))));

            when(arcMemEngine.inject("ctx")).thenReturn(List.of(), List.of());
            when(extractionService.extract("ctx", "DM: The king still lives."))
                    .thenReturn(new ExtractionResult(1, 1, 0, List.of("The king still lives.")));

            var groundTruth = List.of(new SimulationScenario.GroundTruth("f1", "The king is alive"));

            var result = executor.executeTurnFull(
                    "ctx", 3, "The king is dead!", TurnType.ATTACK, List.of(AttackStrategy.CONFIDENT_ASSERTION),
                    "royal court", false, 0, groundTruth, List.of(),
                    Map.of(), null, null, true, null, new HashMap<>());

            verify(extractionService).extract("ctx", "DM: The king still lives.");
            assertThat(result.turn().verdicts()).isNotEmpty();
            assertThat(result.turn().contextTrace().propositionsExtracted()).isEqualTo(1);
            assertThat(result.turn().contextTrace().propositionsPromoted()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("ESTABLISH turn runs extraction only — no drift eval")
    class EstablishTurnParallel {

        @Test
        @DisplayName("ESTABLISH turn only needs extraction branch — drift eval skipped")
        void establishTurnRunsExtractionOnlyNoDrift() {
            var executor = executorWithFlag(true);
            stubDmResponse("The tavern is warm and inviting.");

            when(complianceEnforcer.enforce(any())).thenReturn(ComplianceResult.compliant(Duration.ZERO));
            when(arcMemEngine.inject("ctx")).thenReturn(List.of(), List.of());
            when(extractionService.extract("ctx", "The tavern is warm and inviting."))
                    .thenReturn(new ExtractionResult(2, 0, 0, List.of("The tavern is warm.", "The tavern is inviting.")));

            var result = executor.executeTurnFull(
                    "ctx", 1, "Describe the tavern.", TurnType.ESTABLISH, null,
                    "medieval town", true, 0, List.of(), List.of(),
                    Map.of(), null, null, true, null, new HashMap<>());

            verify(extractionService).extract("ctx", "The tavern is warm and inviting.");
            assertThat(result.turn().verdicts()).isEmpty();
            assertThat(result.turn().contextTrace().propositionsExtracted()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("extraction disabled skips Branch B")
    class ExtractionDisabledParallel {

        @Test
        @DisplayName("extractionEnabled=false skips extraction branch entirely")
        void extractionDisabledSkipsBranchB() {
            var executor = executorWithFlag(true);
            stubDmResponse("The fortress stands strong.");

            when(arcMemEngine.inject("ctx")).thenReturn(List.of(), List.of());

            var result = executor.executeTurnFull(
                    "ctx", 2, "Tell me about the fortress.", TurnType.ESTABLISH, null,
                    "medieval siege", false, 0, List.of(), List.of(),
                    Map.of(), null, null, false, null, new HashMap<>());

            verify(extractionService, never()).extract(anyString(), anyString());

            assertThat(result.turn().contextTrace().propositionsExtracted()).isEqualTo(0);
            assertThat(result.turn().contextTrace().propositionsPromoted()).isEqualTo(0);
        }

        @Test
        @DisplayName("ATTACK turn with extraction disabled only runs drift eval branch")
        void attackTurnExtractionDisabledOnlyRunsDriftEval() {
            var executor = executorWithFlag(true);
            when(chatModel.call(any(Prompt.class)))
                    .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("The bridge still stands.")))))
                    .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(
                            "{\"verdicts\":[{\"fact_id\":\"f1\",\"verdict\":\"CONFIRMED\",\"severity\":\"NONE\",\"explanation\":\"bridge ok\"}]}")))));

            when(arcMemEngine.inject("ctx")).thenReturn(List.of(), List.of());

            var groundTruth = List.of(new SimulationScenario.GroundTruth("f1", "The bridge is intact"));

            var result = executor.executeTurnFull(
                    "ctx", 4, "Destroy the bridge!", TurnType.ATTACK, List.of(AttackStrategy.SUBTLE_REFRAME),
                    "bridge scenario", false, 0, groundTruth, List.of(),
                    Map.of(), null, null, false, null, new HashMap<>());

            verify(extractionService, never()).extract(anyString(), anyString());
            assertThat(result.turn().verdicts()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("ContextTrace merges both branch results after join")
    class ContextTraceMerge {

        @Test
        @DisplayName("context trace includes extraction counts from Branch B after parallel join")
        void contextTraceIncludesExtractionCountsAfterJoin() {
            var executor = executorWithFlag(true);
            stubDmResponse("Ancient ruins hide many secrets.");
            var existingUnit = unit("a1", "The ruins are ancient");

            when(complianceEnforcer.enforce(any())).thenReturn(ComplianceResult.compliant(Duration.ZERO));
            when(arcMemEngine.inject("ctx")).thenReturn(
                    List.of(existingUnit),
                    List.of(existingUnit));
            when(extractionService.extract("ctx", "Ancient ruins hide many secrets."))
                    .thenReturn(new ExtractionResult(3, 2, 0, List.of("fact1", "fact2", "fact3")));

            var result = executor.executeTurnFull(
                    "ctx", 1, "Tell me about ruins.", TurnType.ESTABLISH, null,
                    "ruins exploration", true, 0, List.of(), List.of(),
                    Map.of("a1", existingUnit), null, null, true, null, new HashMap<>());

            var trace = result.turn().contextTrace();
            assertThat(trace.propositionsExtracted()).isEqualTo(3);
            assertThat(trace.propositionsPromoted()).isEqualTo(2);
            assertThat(trace.extractedTexts()).containsExactly("fact1", "fact2", "fact3");
        }
    }
}
