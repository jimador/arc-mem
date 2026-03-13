package dev.arcmem.simulator.compaction;

import dev.arcmem.core.assembly.protection.ProtectedContent;

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

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("CompactionSummaryGenerator — Retry and Fallback (Sections 8.3, 8.4)")
@ExtendWith(MockitoExtension.class)
class CompactionSummaryGeneratorRetryTest {

    private static final Duration SHORT_BACKOFF = Duration.ofMillis(1);

    @Mock
    private ChatModel chatModel;

    private CompactionSummaryGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new CompactionSummaryGenerator(chatModel);
        generator.clearCache();
    }

    private ChatResponse chatResponse(String text) {
        var message = new AssistantMessage(text);
        var generation = new Generation(message);
        return new ChatResponse(List.of(generation));
    }

    @Nested
    @DisplayName("retry behavior")
    class RetryBehavior {

        @Test
        @DisplayName("succeeds on second attempt after first failure — retryCount=1, fallbackUsed=false")
        void failFirstSucceedSecondReturnsRetryCountOne() {
            when(chatModel.call(any(Prompt.class)))
                    .thenThrow(new RuntimeException("LLM unavailable"))
                    .thenReturn(chatResponse("Summary after retry."));

            var result = generator.generateSummary(
                    List.of("Message 1", "Message 2"), "test context",
                    List.of(), 2, SHORT_BACKOFF);

            assertThat(result.summary()).isEqualTo("Summary after retry.");
            assertThat(result.retryCount()).isEqualTo(1);
            assertThat(result.fallbackUsed()).isFalse();
        }

        @Test
        @DisplayName("all attempts fail with maxRetries=2 — extractive fallback used, retryCount=2")
        void allAttemptsFailUsesFallback() {
            when(chatModel.call(any(Prompt.class)))
                    .thenThrow(new RuntimeException("Fail 1"))
                    .thenThrow(new RuntimeException("Fail 2"))
                    .thenThrow(new RuntimeException("Fail 3"));

            var protectedContent = List.of(
                    new ProtectedContent("a-1", "Dragon sleeps", 100, "unit"),
                    new ProtectedContent("a-2", "Sword is cursed", 50, "unit")
            );

            var result = generator.generateSummary(
                    List.of("Message"), "test context",
                    protectedContent, 2, SHORT_BACKOFF);

            assertThat(result.fallbackUsed()).isTrue();
            assertThat(result.retryCount()).isEqualTo(2);
            assertThat(result.summary()).isNotBlank();
        }

        @Test
        @DisplayName("maxRetries=0 with failure — immediate fallback, retryCount=0")
        void zeroRetriesWithFailureImmediateFallback() {
            when(chatModel.call(any(Prompt.class)))
                    .thenThrow(new RuntimeException("Immediate fail"));

            var protectedContent = List.of(
                    new ProtectedContent("a-1", "Important fact", 100, "unit")
            );

            var result = generator.generateSummary(
                    List.of("Message"), "test context",
                    protectedContent, 0, SHORT_BACKOFF);

            assertThat(result.fallbackUsed()).isTrue();
            assertThat(result.retryCount()).isZero();
        }

        @Test
        @DisplayName("successful first attempt — retryCount=0, fallbackUsed=false")
        void successfulFirstAttemptNoRetryNeeded() {
            when(chatModel.call(any(Prompt.class)))
                    .thenReturn(chatResponse("First attempt success."));

            var result = generator.generateSummary(
                    List.of("Message"), "test context",
                    List.of(), 3, SHORT_BACKOFF);

            assertThat(result.summary()).isEqualTo("First attempt success.");
            assertThat(result.retryCount()).isZero();
            assertThat(result.fallbackUsed()).isFalse();
        }
    }

    @Nested
    @DisplayName("extractive fallback")
    class ExtractiveFallback {

        @Test
        @DisplayName("fallback sorts protected content by priority descending and joins with space")
        void fallbackSortsByPriorityDescending() {
            when(chatModel.call(any(Prompt.class)))
                    .thenThrow(new RuntimeException("Fail"));

            var protectedContent = List.of(
                    new ProtectedContent("a-1", "High priority text", 100, "unit"),
                    new ProtectedContent("a-2", "Low priority text", 50, "unit"),
                    new ProtectedContent("a-3", "Medium priority text", 75, "unit")
            );

            var result = generator.generateSummary(
                    List.of("Message"), "test context",
                    protectedContent, 0, SHORT_BACKOFF);

            assertThat(result.fallbackUsed()).isTrue();
            assertThat(result.summary()).isEqualTo("High priority text Medium priority text Low priority text");
        }

        @Test
        @DisplayName("empty protected content yields empty fallback string")
        void emptyProtectedContentYieldsEmptyFallback() {
            when(chatModel.call(any(Prompt.class)))
                    .thenThrow(new RuntimeException("Fail"));

            var result = generator.generateSummary(
                    List.of("Message"), "test context",
                    List.of(), 0, SHORT_BACKOFF);

            assertThat(result.fallbackUsed()).isTrue();
            assertThat(result.summary()).isEmpty();
        }
    }
}
