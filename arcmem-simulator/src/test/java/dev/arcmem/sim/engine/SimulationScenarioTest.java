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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimulationScenario extractionEnabled")
class SimulationScenarioTest {

    @Test
    @DisplayName("defaults extraction to enabled when field is omitted")
    void defaultsExtractionToEnabled() {
        var scenario = scenarioWithExtraction(null);

        assertThat(scenario.isExtractionEnabled()).isTrue();
    }

    @Test
    @DisplayName("honors explicit extraction disable")
    void honorsExplicitExtractionDisable() {
        var scenario = scenarioWithExtraction(false);

        assertThat(scenario.isExtractionEnabled()).isFalse();
    }

    private static SimulationScenario scenarioWithExtraction(Boolean extractionEnabled) {
        return new SimulationScenario(
                "test",
                null,
                null,
                null,
                2,
                1,
                false,
                "setting",
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                List.of(),
                null,
                List.of(),
                "baseline",
                extractionEnabled,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
