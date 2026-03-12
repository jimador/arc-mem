package dev.dunnam.diceanchors.assembly;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CharHeuristicTokenCounter")
class CharHeuristicTokenCounterTest {

    private final CharHeuristicTokenCounter counter = new CharHeuristicTokenCounter();

    @Test
    @DisplayName("estimates tokens using chars divided by four")
    void estimateUsesCharsDividedByFour() {
        assertThat(counter.estimate("abcd")).isEqualTo(1);
        assertThat(counter.estimate("abcdefgh")).isEqualTo(2);
        assertThat(counter.estimate("abc")).isZero();
    }

    @Test
    @DisplayName("returns zero for null or empty input")
    void nullOrEmptyReturnsZero() {
        assertThat(counter.estimate(null)).isZero();
        assertThat(counter.estimate("")).isZero();
    }
}
