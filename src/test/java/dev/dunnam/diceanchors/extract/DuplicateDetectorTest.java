package dev.dunnam.diceanchors.extract;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DuplicateDetector")
class DuplicateDetectorTest {

    private static final String CONTEXT_ID = "test-ctx";

    @Mock private ChatModel chatModel;
    @Mock private AnchorEngine engine;
    @Mock private dev.dunnam.diceanchors.sim.engine.LlmCallService llmCallService;

    private final NormalizedStringDuplicateDetector fastDetector = new NormalizedStringDuplicateDetector();

    private DuplicateDetector detectorWithStrategy(String strategy) {
        var anchorConfig = new DiceAnchorsProperties.AnchorConfig(20, 500, 100, 900, true, 0.65, strategy, "TIERED", true, true, true, 0.6, 400, 200, null);
        var properties = new DiceAnchorsProperties(anchorConfig, null, null, null, null, null, null, new DiceAnchorsProperties.AssemblyConfig(0), null);
        return new DuplicateDetector(chatModel, engine, fastDetector, properties, llmCallService);
    }

    private List<Anchor> singleAnchor(String text) {
        return List.of(Anchor.withoutTrust("a1", text, 500, Authority.PROVISIONAL, false, 0.9, 0));
    }

    private void mockLlmResponse(String response) {
        var generation = new Generation(new AssistantMessage(response));
        var chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(chatResponse);
    }

    @Nested
    @DisplayName("FAST_ONLY strategy")
    class FastOnly {

        @Test
        @DisplayName("returns true for normalized match without LLM call")
        void normalizedMatchNoLlm() {
            var detector = detectorWithStrategy("FAST_ONLY");
            when(engine.inject(CONTEXT_ID)).thenReturn(singleAnchor("The king is dead"));

            var result = detector.isDuplicate(CONTEXT_ID, "THE KING IS DEAD!");

            assertThat(result).isTrue();
            verify(chatModel, never()).call(any(org.springframework.ai.chat.prompt.Prompt.class));
        }

        @Test
        @DisplayName("returns false for non-match without LLM call")
        void noMatchNoLlm() {
            var detector = detectorWithStrategy("FAST_ONLY");
            when(engine.inject(CONTEXT_ID)).thenReturn(singleAnchor("The king is dead"));

            var result = detector.isDuplicate(CONTEXT_ID, "The queen is alive");

            assertThat(result).isFalse();
            verify(chatModel, never()).call(any(org.springframework.ai.chat.prompt.Prompt.class));
        }
    }

    @Nested
    @DisplayName("LLM_ONLY strategy")
    class LlmOnly {

        @Test
        @DisplayName("skips fast-path and calls LLM")
        void skipsFastPathCallsLlm() {
            var detector = detectorWithStrategy("LLM_ONLY");
            when(engine.inject(CONTEXT_ID)).thenReturn(singleAnchor("The king is dead"));
            mockLlmResponse("DUPLICATE");

            var result = detector.isDuplicate(CONTEXT_ID, "THE KING IS DEAD!");

            assertThat(result).isTrue();
            verify(chatModel).call(any(org.springframework.ai.chat.prompt.Prompt.class));
        }

        @Test
        @DisplayName("returns LLM unique result")
        void llmUnique() {
            var detector = detectorWithStrategy("LLM_ONLY");
            when(engine.inject(CONTEXT_ID)).thenReturn(singleAnchor("The king is dead"));
            mockLlmResponse("UNIQUE");

            var result = detector.isDuplicate(CONTEXT_ID, "Something different");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("FAST_THEN_LLM strategy")
    class FastThenLlm {

        @Test
        @DisplayName("returns true on fast-path match without LLM call")
        void fastMatchSkipsLlm() {
            var detector = detectorWithStrategy("FAST_THEN_LLM");
            when(engine.inject(CONTEXT_ID)).thenReturn(singleAnchor("The king is dead"));

            var result = detector.isDuplicate(CONTEXT_ID, "the king is dead");

            assertThat(result).isTrue();
            verify(chatModel, never()).call(any(org.springframework.ai.chat.prompt.Prompt.class));
        }

        @Test
        @DisplayName("falls back to LLM when fast-path misses")
        void fallsBackToLlm() {
            var detector = detectorWithStrategy("FAST_THEN_LLM");
            when(engine.inject(CONTEXT_ID)).thenReturn(singleAnchor("The king is dead"));
            mockLlmResponse("DUPLICATE");

            var result = detector.isDuplicate(CONTEXT_ID, "The monarch has perished");

            assertThat(result).isTrue();
            verify(chatModel).call(any(org.springframework.ai.chat.prompt.Prompt.class));
        }

        @Test
        @DisplayName("returns false when both paths say unique")
        void bothUnique() {
            var detector = detectorWithStrategy("FAST_THEN_LLM");
            when(engine.inject(CONTEXT_ID)).thenReturn(singleAnchor("The king is dead"));
            mockLlmResponse("UNIQUE");

            var result = detector.isDuplicate(CONTEXT_ID, "The tavern is warm");

            assertThat(result).isFalse();
        }
    }

    @Test
    @DisplayName("returns false for empty anchor list regardless of strategy")
    void emptyAnchorsReturnsFalse() {
        var detector = detectorWithStrategy("FAST_THEN_LLM");
        when(engine.inject(CONTEXT_ID)).thenReturn(List.of());

        assertThat(detector.isDuplicate(CONTEXT_ID, "anything")).isFalse();
        verify(chatModel, never()).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }
}
