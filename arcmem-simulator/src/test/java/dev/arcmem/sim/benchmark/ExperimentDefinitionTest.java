package dev.arcmem.simulator.benchmark;
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExperimentDefinition")
class ExperimentDefinitionTest {

    private static final List<AblationCondition> TWO_CONDITIONS = List.of(
            AblationCondition.FULL_AWMU,
            AblationCondition.NO_AWMU
    );

    private static final List<String> THREE_SCENARIOS = List.of("scenario-a", "scenario-b", "scenario-c");

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("valid construction with all fields")
        void validConstructionWithAllFields() {
            var def = new ExperimentDefinition(
                    "test-experiment",
                    TWO_CONDITIONS,
                    THREE_SCENARIOS,
                    5,
                    Optional.of("gpt-4o")
            );

            assertThat(def.name()).isEqualTo("test-experiment");
            assertThat(def.conditions()).hasSize(2);
            assertThat(def.scenarioIds()).hasSize(3);
            assertThat(def.repetitionsPerCell()).isEqualTo(5);
            assertThat(def.evaluatorModel()).contains("gpt-4o");
        }

        @Test
        @DisplayName("null evaluatorModel defaults to Optional.empty()")
        void nullEvaluatorModelDefaultsToEmpty() {
            var def = new ExperimentDefinition(
                    "test-experiment",
                    TWO_CONDITIONS,
                    THREE_SCENARIOS,
                    2,
                    null
            );

            assertThat(def.evaluatorModel()).isEmpty();
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("empty conditions list throws IllegalArgumentException")
        void emptyConditionsThrowsIae() {
            assertThatThrownBy(() -> new ExperimentDefinition(
                    "bad", List.of(), THREE_SCENARIOS, 2, Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("condition");
        }

        @Test
        @DisplayName("empty scenarioIds list throws IllegalArgumentException")
        void emptyScenarioIdsThrowsIae() {
            assertThatThrownBy(() -> new ExperimentDefinition(
                    "bad", TWO_CONDITIONS, List.of(), 2, Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("scenario");
        }

        @Test
        @DisplayName("repetitionsPerCell below 2 throws IllegalArgumentException with minimum message")
        void repetitionsBelowMinimumThrowsIae() {
            assertThatThrownBy(() -> new ExperimentDefinition(
                    "bad", TWO_CONDITIONS, THREE_SCENARIOS, 1, Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least 2 repetitions");
        }
    }

    @Nested
    @DisplayName("totalCells")
    class TotalCells {

        @Test
        @DisplayName("returns conditions.size() * scenarioIds.size()")
        void returnsConditionsTimesScenarios() {
            var def = new ExperimentDefinition(
                    "matrix", TWO_CONDITIONS, THREE_SCENARIOS, 3, Optional.empty());

            assertThat(def.totalCells()).isEqualTo(2 * 3);
        }
    }

    @Nested
    @DisplayName("totalRuns")
    class TotalRuns {

        @Test
        @DisplayName("returns totalCells() * repetitionsPerCell")
        void returnsCellsTimesRepetitions() {
            var def = new ExperimentDefinition(
                    "matrix", TWO_CONDITIONS, THREE_SCENARIOS, 4, Optional.empty());

            assertThat(def.totalRuns()).isEqualTo(2 * 3 * 4);
        }
    }
}
