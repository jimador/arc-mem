package dev.arcmem.core.assembly.budget;
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
