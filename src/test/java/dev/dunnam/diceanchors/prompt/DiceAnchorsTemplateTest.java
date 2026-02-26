package dev.dunnam.diceanchors.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiceAnchorsTemplateTest {

    private static final String TEMPLATE = "prompts/dice-anchors.jinja";

    private Map<String, Object> baseVars() {
        var vars = new HashMap<String, Object>();
        vars.put("persona", "dm");
        vars.put("tiered", true);
        vars.put("proposition_block", null);
        return vars;
    }

    private Map<String, Object> anchorMap(String text, int rank) {
        return Map.of("text", text, "rank", rank);
    }

    @Nested
    @DisplayName("Revision carveout removed")
    class RevisionCarveoutRemoved {

        @Test
        @DisplayName("noRevisableAnnotationsOnAnyAnchor")
        void noRevisableAnnotationsOnAnyAnchor() {
            var vars = baseVars();
            vars.put("provisional_anchors", List.of(anchorMap("Anakin is a wizard", 500)));
            vars.put("unreliable_anchors", List.of(anchorMap("The tavern is busy", 400)));
            vars.put("reliable_anchors", List.of(anchorMap("The king rules", 700)));
            vars.put("anchors", List.of(
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
            vars.put("provisional_anchors", List.of(anchorMap("test", 500)));
            vars.put("anchors", List.of(Map.of("text", "test", "rank", 500, "authority", "PROVISIONAL")));

            var result = PromptTemplates.render(TEMPLATE, vars);

            assertThat(result).doesNotContain("reviseFact");
            assertThat(result).doesNotContain("[revisable]");
        }

        @Test
        @DisplayName("noRevisionExceptionInVerificationProtocol")
        void noRevisionExceptionInVerificationProtocol() {
            var vars = baseVars();
            vars.put("provisional_anchors", List.of(anchorMap("test", 500)));
            vars.put("anchors", List.of(Map.of("text", "test", "rank", 500, "authority", "PROVISIONAL")));

            var result = PromptTemplates.render(TEMPLATE, vars);

            assertThat(result).doesNotContain("Exception:");
            assertThat(result).doesNotContain("reviseFact tool");
        }
    }
}
