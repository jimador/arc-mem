package dev.dunnam.diceanchors.assembly;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SummaryResult (Section 8.2)")
class SummaryResultTest {

    @Nested
    @DisplayName("record construction and field access")
    class ConstructionAndAccess {

        @Test
        @DisplayName("returns summary text, retryCount, and fallbackUsed=false")
        void constructionWithFallbackNotUsed() {
            var result = new SummaryResult("The dragon was defeated.", 0, false);

            assertThat(result.summary()).isEqualTo("The dragon was defeated.");
            assertThat(result.retryCount()).isZero();
            assertThat(result.fallbackUsed()).isFalse();
        }

        @Test
        @DisplayName("returns summary text, retryCount, and fallbackUsed=true")
        void constructionWithFallbackUsed() {
            var result = new SummaryResult("Extractive fallback text.", 3, true);

            assertThat(result.summary()).isEqualTo("Extractive fallback text.");
            assertThat(result.retryCount()).isEqualTo(3);
            assertThat(result.fallbackUsed()).isTrue();
        }
    }
}
