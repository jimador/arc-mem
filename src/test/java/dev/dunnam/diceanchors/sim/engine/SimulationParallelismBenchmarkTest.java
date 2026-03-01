package dev.dunnam.diceanchors.sim.engine;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.CompliancePolicyMode;
import dev.dunnam.diceanchors.anchor.DedupStrategy;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.CompliancePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Performance smoke test comparing sequential vs parallel post-response execution.
 * <p>
 * Uses 300 ms artificial delays per LLM/extraction call to simulate real model latency
 * without incurring API costs. Each ATTACK turn involves:
 * <ul>
 *   <li>DM response LLM call — always sequential (300 ms)</li>
 *   <li>Branch A: drift-evaluation LLM call (300 ms)</li>
 *   <li>Branch B: extraction service call (300 ms)</li>
 * </ul>
 * Sequential per turn: 300 + 300 + 300 = ~900 ms<br>
 * Parallel per turn:   300 + max(300, 300) = ~600 ms<br>
 * Expected speedup over 4 turns: ~1.5x
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Parallelism benchmark: sequential vs parallel post-response")
class SimulationParallelismBenchmarkTest {

    private static final long LLM_DELAY_MS = 300;
    private static final int TURN_COUNT = 4;

    @Mock private ChatModelHolder chatModel;
    @Mock private dev.dunnam.diceanchors.anchor.AnchorEngine anchorEngine;
    @Mock private dev.dunnam.diceanchors.persistence.AnchorRepository anchorRepository;
    @Mock private SimulationExtractionService extractionService;

    @Test
    @DisplayName("parallel mode is faster than sequential for ATTACK turns with concurrent drift+extraction")
    void parallelFasterThanSequentialForAttackTurns() throws Exception {
        setupMocks(0);
        runTurns(true, 1);

        setupMocks(LLM_DELAY_MS);
        long sequentialMs = measureMs(() -> runTurns(false, TURN_COUNT));

        setupMocks(LLM_DELAY_MS);
        long parallelMs = measureMs(() -> runTurns(true, TURN_COUNT));

        double speedup = (double) sequentialMs / parallelMs;
        System.out.printf(
                "%n╔══════════════════════════════════════════╗%n" +
                "║      Parallelism Benchmark Results       ║%n" +
                "╠══════════════════════════════════════════╣%n" +
                "║  Scenario : %d ATTACK turns               ║%n" +
                "║  LLM delay: %d ms / call                 ║%n" +
                "║  Sequential: %5d ms                     ║%n" +
                "║  Parallel  : %5d ms                     ║%n" +
                "║  Speedup   : %.2fx                        ║%n" +
                "╚══════════════════════════════════════════╝%n",
                TURN_COUNT, LLM_DELAY_MS, sequentialMs, parallelMs, speedup);

        assertThat(parallelMs)
                .as("Parallel execution should be at least 30%% faster than sequential")
                .isLessThan((long) (sequentialMs * 0.80));

        assertThat(speedup)
                .as("Speedup factor should be >= 1.3x")
                .isGreaterThanOrEqualTo(1.3);
    }

    private void setupMocks(long delayMs) throws Exception {
        when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
            if (delayMs > 0) Thread.sleep(delayMs);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(
                    "{\"verdicts\":[{\"fact_id\":\"f1\",\"verdict\":\"CONFIRMED\"," +
                    "\"severity\":\"NONE\",\"explanation\":\"ok\"}]}"))));
        });

        when(anchorEngine.inject(anyString())).thenReturn(List.of(
                Anchor.withoutTrust("a1", "The king lives", 600, Authority.RELIABLE, false, 0.9, 2)));

        when(extractionService.extract(anyString(), anyString())).thenAnswer(inv -> {
            if (delayMs > 0) Thread.sleep(delayMs);
            return new ExtractionResult(2, 1, 0, List.of("fact-a", "fact-b"));
        });
    }

    private void runTurns(boolean parallel, int turnCount) {
        var executor = buildExecutor(parallel);
        var groundTruth = List.of(new SimulationScenario.GroundTruth("f1", "The king is alive"));

        for (int i = 1; i <= turnCount; i++) {
            executor.executeTurnFull(
                    "bench-ctx", i,
                    "The king is dead!", TurnType.ATTACK, List.of(AttackStrategy.CONFIDENT_ASSERTION),
                    "royal court", false, 0,
                    groundTruth, List.of(),
                    Map.of(), null, null,
                    true, null, new HashMap<>());
        }
    }

    private SimulationTurnExecutor buildExecutor(boolean parallel) {
        var properties = new DiceAnchorsProperties(
                new DiceAnchorsProperties.AnchorConfig(20, 500, 100, 900, true, 0.65,
                        DedupStrategy.FAST_THEN_LLM, CompliancePolicyMode.TIERED, true, true, true, 0.6, 400, 200, null, null, null, null),
                null, null, null,
                new DiceAnchorsProperties.SimConfig("gpt-4.1-mini", 30, 30, 10, parallel, 4),
                null, null,
                new DiceAnchorsProperties.AssemblyConfig(0), null, null, null);
        return new SimulationTurnExecutor(
                chatModel,
                anchorEngine,
                anchorRepository,
                properties,
                CompliancePolicy.flat(),
                text -> Math.max(1, text.length() / 4),
                extractionService,
                null);
    }

    private static long measureMs(ThrowingRunnable action) throws Exception {
        long start = System.currentTimeMillis();
        action.run();
        return System.currentTimeMillis() - start;
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
