package dev.arcmem.core.memory.model;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MemoryUnit")
class MemoryUnitTest {

    @Nested
    @DisplayName("clampRank")
    class ClampRank {

        @Test
        @DisplayName("clamps to minimum when below 100")
        void clampRank_belowMinimum_clampsTo100() {
            assertThat(MemoryUnit.clampRank(0)).isEqualTo(MemoryUnit.MIN_RANK);
            assertThat(MemoryUnit.clampRank(-50)).isEqualTo(MemoryUnit.MIN_RANK);
            assertThat(MemoryUnit.clampRank(99)).isEqualTo(MemoryUnit.MIN_RANK);
        }

        @Test
        @DisplayName("clamps to maximum when above 900")
        void clampRank_aboveMaximum_clampsTo900() {
            assertThat(MemoryUnit.clampRank(901)).isEqualTo(MemoryUnit.MAX_RANK);
            assertThat(MemoryUnit.clampRank(1000)).isEqualTo(MemoryUnit.MAX_RANK);
            assertThat(MemoryUnit.clampRank(Integer.MAX_VALUE)).isEqualTo(MemoryUnit.MAX_RANK);
        }

        @Test
        @DisplayName("returns unchanged rank when within [100, 900]")
        void clampRank_withinRange_returnsUnchanged() {
            assertThat(MemoryUnit.clampRank(100)).isEqualTo(100);
            assertThat(MemoryUnit.clampRank(500)).isEqualTo(500);
            assertThat(MemoryUnit.clampRank(900)).isEqualTo(900);
            assertThat(MemoryUnit.clampRank(350)).isEqualTo(350);
        }
    }
}
