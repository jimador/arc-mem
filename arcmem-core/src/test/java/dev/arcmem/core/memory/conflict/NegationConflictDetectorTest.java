package dev.arcmem.core.memory.conflict;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NegationConflictDetector")
class NegationConflictDetectorTest {

    private final NegationConflictDetector detector = new NegationConflictDetector();

    @Nested
    @DisplayName("detect")
    class Detect {

        @Test
        @DisplayName("detects negation conflict between positive and negative statements")
        void detectsNegationConflict() {
            var units = List.of(
                    MemoryUnit.withoutTrust("1", "The king is alive and well", 700, Authority.RELIABLE, false, 0.9, 0)
            );
            var conflicts = detector.detect("The king is not alive", units);
            assertThat(conflicts).isNotEmpty();
            assertThat(conflicts.getFirst().existing().id()).isEqualTo("1");
        }

        @Test
        @DisplayName("no conflict for unrelated statements")
        void noConflictForUnrelatedStatements() {
            var units = List.of(
                    MemoryUnit.withoutTrust("1", "The sword glows blue", 500, Authority.PROVISIONAL, false, 0.8, 0)
            );
            var conflicts = detector.detect("The tavern serves good ale", units);
            assertThat(conflicts).isEmpty();
        }

        @Test
        @DisplayName("no conflict when both statements have same polarity")
        void noConflictSamePolarity() {
            var units = List.of(
                    MemoryUnit.withoutTrust("1", "The dragon is not friendly", 600, Authority.RELIABLE, false, 0.9, 0)
            );
            var conflicts = detector.detect("The dragon is not tame", units);
            assertThat(conflicts).isEmpty();
        }

        @Test
        @DisplayName("empty memory unit list produces no conflicts")
        void emptyUnitsNoConflicts() {
            var conflicts = detector.detect("Any statement", List.of());
            assertThat(conflicts).isEmpty();
        }
    }
}
