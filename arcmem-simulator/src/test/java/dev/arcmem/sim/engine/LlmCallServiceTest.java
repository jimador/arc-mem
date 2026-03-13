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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmCallService")
class LlmCallServiceTest {

    @Mock
    private ChatModel chatModel;

    private LlmCallService service;

    @BeforeEach
    void setUp() {
        var properties = propertiesWithTimeout(2);
        service = new LlmCallService(chatModel, properties);
    }

    @Nested
    @DisplayName("call()")
    class Call {

        @Test
        @DisplayName("returns LLM response text on successful call")
        void callSuccessReturnsText() {
            stubChatModel("The dragon attacks!");

            var result = service.call("You are a DM.", "What does the dragon do?");

            assertThat(result).isEqualTo("The dragon attacks!");
        }

        @Test
        @DisplayName("propagates ChatModel exception as RuntimeException")
        void callChatModelThrowsWrapsInRuntimeException() {
            when(chatModel.call(any(Prompt.class)))
                    .thenThrow(new RuntimeException("API error"));

            assertThatThrownBy(() -> service.call("sys", "user"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("API error");
        }

        @Test
        @DisplayName("throws LlmCallTimeoutException when model exceeds timeout")
        void callTimeoutThrowsLlmCallTimeoutException() {
            when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
                Thread.sleep(5_000);
                return stubResponse("late response");
            });

            assertThatThrownBy(() -> service.call("sys", "user"))
                    .isInstanceOf(LlmCallTimeoutException.class)
                    .hasMessageContaining("timed out");
        }
    }

    @Nested
    @DisplayName("callBatched()")
    class CallBatched {

        @Test
        @DisplayName("returns LLM response text for batched prompt")
        void callBatchedSuccessReturnsText() {
            stubChatModel("Batch result");

            var result = service.callBatched("sys", "batch payload");

            assertThat(result).isEqualTo("Batch result");
        }

        @Test
        @DisplayName("propagates ChatModel exception as RuntimeException")
        void callBatchedChatModelThrowsWrapsInRuntimeException() {
            when(chatModel.call(any(Prompt.class)))
                    .thenThrow(new RuntimeException("batch API error"));

            assertThatThrownBy(() -> service.callBatched("sys", "user"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("batch API error");
        }

        @Test
        @DisplayName("succeeds when response arrives between single timeout and batch timeout")
        void callBatchedUsesExtendedTimeout() throws Exception {
            // Sleep 3s: longer than callTimeoutSeconds (2s) but shorter than batchCallTimeoutSeconds (4s).
            // callBatched() must succeed, proving it uses the 4s batch timeout not the 2s single timeout.
            when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
                Thread.sleep(3_000);
                return stubResponse("batch response after delay");
            });

            var result = service.callBatched("sys", "batch payload");

            assertThat(result).isEqualTo("batch response after delay");
        }
    }

    @Nested
    @DisplayName("concurrent safety")
    class ConcurrentSafety {

        @Test
        @DisplayName("multiple concurrent calls all return correct results")
        void concurrentCallsAllSucceed() throws Exception {
            stubChatModel("concurrent response");

            int threadCount = 10;
            var executor = Executors.newFixedThreadPool(threadCount);
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                tasks.add(() -> service.call("sys", "user"));
            }

            var futures = executor.invokeAll(tasks);
            executor.shutdown();

            for (Future<String> future : futures) {
                assertThat(future.get()).isEqualTo("concurrent response");
            }
        }
    }

    private void stubChatModel(String text) {
        when(chatModel.call(any(Prompt.class))).thenReturn(stubResponse(text));
    }

    private static ChatResponse stubResponse(String text) {
        var assistantMessage = new AssistantMessage(text);
        var generation = new Generation(assistantMessage);
        return new ChatResponse(List.of(generation));
    }

    private static ArcMemProperties propertiesWithTimeout(int timeoutSeconds) {
        return new ArcMemProperties(
                new ArcMemProperties.UnitConfig(20, 500, 100, 900, true, 0.65, DedupStrategy.FAST_THEN_LLM, CompliancePolicyMode.TIERED, true, true, true, 0.6, 400, 200, null, null, null, null, null),
                new ArcMemProperties.MemoryConfig(true, null, null, "text-embedding-3-small", 20, 5, 2),
                new ArcMemProperties.PersistenceConfig(false),
                new ArcMemProperties.ConflictDetectionConfig(dev.arcmem.core.memory.conflict.ConflictStrategy.LLM, "gpt-4o-nano"),
                new ArcMemProperties.AssemblyConfig(0, false, dev.arcmem.core.assembly.compliance.EnforcementStrategy.PROMPT_ONLY),
                null, null, null, null, null, null, null,
                new ArcMemProperties.LlmCallConfig(timeoutSeconds, 10));
    }
}
