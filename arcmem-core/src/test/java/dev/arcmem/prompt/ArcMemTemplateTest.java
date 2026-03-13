package dev.arcmem.core.prompt;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArcMemTemplateTest {

    private static final String TEMPLATE = "prompts/arc-mem.jinja";

    private Map<String, Object> baseVars() {
        var vars = new HashMap<String, Object>();
        vars.put("persona", "dm");
        vars.put("tiered", true);
        vars.put("proposition_block", null);
        return vars;
    }

    private Map<String, Object> unitMap(String text, int rank) {
        return Map.of("text", text, "rank", rank);
    }

    @Nested
    @DisplayName("Revision carveout removed")
    class RevisionCarveoutRemoved {

        @Test
        @DisplayName("noRevisableAnnotationsOnAnyMemoryUnit")
        void noRevisableAnnotationsOnAnyUnit() {
            var vars = baseVars();
            vars.put("provisional_units", List.of(unitMap("Anakin is a wizard", 500)));
            vars.put("unreliable_units", List.of(unitMap("The tavern is busy", 400)));
            vars.put("reliable_units", List.of(unitMap("The king rules", 700)));
            vars.put("units", List.of(
                    Map.of("text", "Anakin is a wizard", "rank", 500, "authority", "PROVISIONAL"),
                    Map.of("text", "The tavern is busy", "rank", 400, "authority", "UNRELIABLE"),
                    Map.of("text", "The king rules", "rank", 700, "authority", "RELIABLE")));

            var result = PromptTemplates.render(TEMPLATE, vars);

            assertThat(result).doesNotContain("[revisable]");
        }

        @Test
        @DisplayName("noReviseFactToolInCriticalInstructions")
        void noReviseFactToolInCriticalInstructions() {
            var vars = baseVars();
            vars.put("provisional_units", List.of(unitMap("test", 500)));
            vars.put("units", List.of(Map.of("text", "test", "rank", 500, "authority", "PROVISIONAL")));

            var result = PromptTemplates.render(TEMPLATE, vars);

            assertThat(result).doesNotContain("reviseFact");
            assertThat(result).doesNotContain("[revisable]");
        }

        @Test
        @DisplayName("noRevisionExceptionInVerificationProtocol")
        void noRevisionExceptionInVerificationProtocol() {
            var vars = baseVars();
            vars.put("provisional_units", List.of(unitMap("test", 500)));
            vars.put("units", List.of(Map.of("text", "test", "rank", 500, "authority", "PROVISIONAL")));

            var result = PromptTemplates.render(TEMPLATE, vars);

            assertThat(result).doesNotContain("Exception:");
            assertThat(result).doesNotContain("reviseFact tool");
        }
    }
}
