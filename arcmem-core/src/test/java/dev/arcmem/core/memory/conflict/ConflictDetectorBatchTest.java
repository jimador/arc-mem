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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConflictDetector batch operations")
class ConflictDetectorBatchTest {

    private MemoryUnit unit(String id, String text) {
        return MemoryUnit.withoutTrust(id, text, 500, Authority.PROVISIONAL, false, 0.9, 0);
    }

    @Nested
    @DisplayName("default batchDetect implementation")
    class DefaultBatchDetect {

        @Test
        @DisplayName("delegates detect call for each candidate in sequence")
        void batchDetectDefaultDelegatesPerCandidate() {
            var units = List.of(unit("a1", "The sky is blue"));
            var conflict = new ConflictDetector.Conflict(
                    unit("a1", "The sky is blue"), "The sky is not blue", 0.9, "negation");

            // Concrete anonymous implementation using the default batchDetect
            ConflictDetector impl = (incomingText, existingUnits) -> {
                if (incomingText.contains("not")) {
                    return List.of(conflict);
                }
                return List.of();
            };

            var candidates = List.of("The sky is not blue", "The grass is green");
            var result = impl.batchDetect(candidates, units);

            assertThat(result).hasSize(2);
            assertThat(result.get("The sky is not blue")).hasSize(1);
            assertThat(result.get("The grass is green")).isEmpty();
        }

        @Test
        @DisplayName("empty candidate list returns empty map")
        void batchDetectEmptyListReturnsEmptyMap() {
            var units = List.of(unit("a1", "Some fact"));
            ConflictDetector impl = (incomingText, existingUnits) -> List.of();

            var result = impl.batchDetect(List.of(), units);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("default method calls detect once per candidate")
        void batchDetectDefaultCallsDetectPerCandidate() {
            var units = List.of(unit("a1", "unit text"));
            var callCount = new AtomicInteger(0);
            var calledWith = new ArrayList<String>();

            ConflictDetector impl = (incomingText, existingUnits) -> {
                callCount.incrementAndGet();
                calledWith.add(incomingText);
                return List.of();
            };

            var candidates = List.of("candidate one", "candidate two", "candidate three");
            impl.batchDetect(candidates, units);

            assertThat(callCount.get()).isEqualTo(3);
            assertThat(calledWith).containsExactlyInAnyOrder("candidate one", "candidate two", "candidate three");
        }

        @Test
        @DisplayName("returns all candidates as keys in result map")
        void batchDetectReturnsAllCandidateKeys() {
            ConflictDetector impl = (incomingText, existingUnits) -> List.of();
            var units = List.of(unit("a1", "some unit"));
            var candidates = List.of("alpha", "beta", "gamma");

            var result = impl.batchDetect(candidates, units);

            assertThat(result).containsOnlyKeys("alpha", "beta", "gamma");
        }
    }

    @Nested
    @DisplayName("NegationConflictDetector batch override")
    class NegationBatchDetect {

        private final NegationConflictDetector detector = new NegationConflictDetector();

        @Test
        @DisplayName("returns conflict for negation match")
        void batchDetectNegationMatch() {
            var units = List.of(unit("a1", "The king is dead"));
            var candidates = List.of("The king is not dead");

            var result = detector.batchDetect(candidates, units);

            assertThat(result).containsKey("The king is not dead");
            assertThat(result.get("The king is not dead")).isNotEmpty();
        }

        @Test
        @DisplayName("returns empty conflicts for non-negation candidates")
        void batchDetectNoConflicts() {
            var units = List.of(unit("a1", "The castle stands tall"));
            var candidates = List.of("The queen arrived at court");

            var result = detector.batchDetect(candidates, units);

            assertThat(result).containsKey("The queen arrived at court");
            assertThat(result.get("The queen arrived at court")).isEmpty();
        }

        @Test
        @DisplayName("handles multiple candidates correctly")
        void batchDetectMultipleCandidates() {
            var units = List.of(unit("a1", "The king is alive"));
            var candidates = List.of(
                    "The king is not alive",
                    "The queen visited",
                    "The king is not breathing alive"
            );

            var result = detector.batchDetect(candidates, units);

            assertThat(result).hasSize(3);
            // "The king is not alive" vs "The king is alive": negation mismatch, high overlap -> conflict
            assertThat(result.get("The king is not alive")).isNotEmpty();
            // "The queen visited" has no overlap with "The king is alive"
            assertThat(result.get("The queen visited")).isEmpty();
            // "The king is not breathing alive" vs "The king is alive": negation mismatch, overlap present -> conflict
            assertThat(result.get("The king is not breathing alive")).isNotEmpty();
        }

        @Test
        @DisplayName("empty candidate list returns empty map")
        void batchDetectEmptyReturnsEmpty() {
            var units = List.of(unit("a1", "some unit"));

            var result = detector.batchDetect(List.of(), units);

            assertThat(result).isEmpty();
        }
    }
}
