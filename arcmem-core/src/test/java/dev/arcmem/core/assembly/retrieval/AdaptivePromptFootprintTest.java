package dev.arcmem.core.assembly.retrieval;
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

import dev.arcmem.core.prompt.PromptPathConstants;
import dev.arcmem.core.prompt.PromptTemplates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Adaptive Prompt Footprint")
class AdaptivePromptFootprintTest {

    @Nested
    @DisplayName("Template Files")
    class TemplateFilesTest {

        @Test
        @DisplayName("all four templates load successfully")
        void allTemplatesLoad() {
            var provisional = PromptTemplates.load(PromptPathConstants.UNIT_TEMPLATE_PROVISIONAL);
            var unreliable = PromptTemplates.load(PromptPathConstants.UNIT_TEMPLATE_UNRELIABLE);
            var reliable = PromptTemplates.load(PromptPathConstants.UNIT_TEMPLATE_RELIABLE);
            var canon = PromptTemplates.load(PromptPathConstants.UNIT_TEMPLATE_CANON);

            assertThat(provisional).isNotEmpty();
            assertThat(unreliable).isNotEmpty();
            assertThat(reliable).isNotEmpty();
            assertThat(canon).isNotEmpty();
        }

        @Test
        @DisplayName("provisional template includes rank and low-confidence note")
        void provisionalTemplateVerbose() {
            var vars = Map.of(
                    "index", 1,
                    "text", "The castle has a moat",
                    "rank", 300);
            var rendered = PromptTemplates.render(PromptPathConstants.UNIT_TEMPLATE_PROVISIONAL, vars);

            assertThat(rendered).contains("The castle has a moat");
            assertThat(rendered).contains("300");
            assertThat(rendered).contains("unverified");
        }

        @Test
        @DisplayName("unreliable template includes rank but no low-confidence note")
        void unreliableTemplateModerate() {
            var vars = Map.of(
                    "index", 1,
                    "text", "The castle has a moat",
                    "rank", 400);
            var rendered = PromptTemplates.render(PromptPathConstants.UNIT_TEMPLATE_UNRELIABLE, vars);

            assertThat(rendered).contains("The castle has a moat");
            assertThat(rendered).contains("400");
            assertThat(rendered).doesNotContain("unverified");
        }

        @Test
        @DisplayName("reliable template omits rank")
        void reliableTemplateCondensed() {
            var vars = Map.of(
                    "index", 1,
                    "text", "The castle has a moat",
                    "rank", 500);
            var rendered = PromptTemplates.render(PromptPathConstants.UNIT_TEMPLATE_RELIABLE, vars);

            assertThat(rendered).contains("The castle has a moat");
            assertThat(rendered).doesNotContain("rank");
        }

        @Test
        @DisplayName("canon template is minimal with text only")
        void canonTemplateMinimal() {
            var vars = Map.of(
                    "index", 1,
                    "text", "The king is named Aldric",
                    "rank", 800);
            var rendered = PromptTemplates.render(PromptPathConstants.UNIT_TEMPLATE_CANON, vars);

            assertThat(rendered).contains("The king is named Aldric");
            assertThat(rendered).doesNotContain("rank");
            assertThat(rendered).doesNotContain("800");
        }

        @Test
        @DisplayName("provisional template produces longer output than canon for same text")
        void provisionalLongerThanCanon() {
            var text = "The castle has a moat";
            var vars = Map.of(
                    "index", 1,
                    "text", text,
                    "rank", 300);

            var provisional = PromptTemplates.render(PromptPathConstants.UNIT_TEMPLATE_PROVISIONAL, vars);
            var canon = PromptTemplates.render(PromptPathConstants.UNIT_TEMPLATE_CANON, vars);

            assertThat(provisional.length()).isGreaterThan(canon.length());
        }
    }

    @Nested
    @DisplayName("Template Selection")
    class TemplateSelectionTest {

        @Test
        @DisplayName("disabled mode uses uniform template for all memory units")
        void disabledModeUniform() {
            var compliance = mockCompliancePolicy();
            var engine = mockArcMemEngine(List.of(
                    testUnit("unit1", Authority.CANON, 700),
                    testUnit("unit2", Authority.RELIABLE, 600),
                    testUnit("unit3", Authority.UNRELIABLE, 400),
                    testUnit("unit4", Authority.PROVISIONAL, 300)));

            var ref = new ArcMemLlmReference(engine, "ctx-1", 10, compliance, 0, null, null, null, null, false);
            var content = ref.getContent();

            assertThat(content).contains("CANON FACTS");
            assertThat(content).contains("RELIABLE FACTS");
            assertThat(content).contains("UNRELIABLE FACTS");
            assertThat(content).contains("PROVISIONAL FACTS");
        }

        @Test
        @DisplayName("enabled mode selects canon template for canon memory units")
        void enabledModeCanonTemplate() {
            var compliance = mockCompliancePolicy();
            var engine = mockArcMemEngine(List.of(
                    testUnit("unit1", Authority.CANON, 700)));

            var ref = new ArcMemLlmReference(engine, "ctx-1", 10, compliance, 0, null, null, null, null, true);
            var content = ref.getContent();

            assertThat(content).contains("CANON FACTS");
            assertThat(content).doesNotContain("rank: 700");
        }

        @Test
        @DisplayName("enabled mode selects provisional template for provisional memory units")
        void enabledModeProvisionalTemplate() {
            var compliance = mockCompliancePolicy();
            var engine = mockArcMemEngine(List.of(
                    testUnit("unit1", Authority.PROVISIONAL, 300)));

            var ref = new ArcMemLlmReference(engine, "ctx-1", 10, compliance, 0, null, null, null, null, true);
            var content = ref.getContent();

            assertThat(content).contains("PROVISIONAL FACTS");
            assertThat(content).contains("unverified");
        }

        @Test
        @DisplayName("enabled mode with mixed authorities uses correct templates per tier")
        void enabledModeMixedAuthorities() {
            var compliance = mockCompliancePolicy();
            var engine = mockArcMemEngine(List.of(
                    testUnit("canon fact", Authority.CANON, 800),
                    testUnit("reliable fact", Authority.RELIABLE, 600),
                    testUnit("unreliable fact", Authority.UNRELIABLE, 400),
                    testUnit("provisional fact", Authority.PROVISIONAL, 300)));

            var ref = new ArcMemLlmReference(engine, "ctx-1", 10, compliance, 0, null, null, null, null, true);
            var content = ref.getContent();

            assertThat(content).contains("CANON FACTS");
            assertThat(content).contains("RELIABLE FACTS");
            assertThat(content).contains("UNRELIABLE FACTS");
            assertThat(content).contains("PROVISIONAL FACTS");
        }

        @Test
        @DisplayName("empty memory unit set produces empty content")
        void emptyUnitSet() {
            var compliance = mockCompliancePolicy();
            var engine = mockArcMemEngine(List.of());

            var ref = new ArcMemLlmReference(engine, "ctx-1", 10, compliance, 0, null, null, null, null, true);
            var content = ref.getContent();

            assertThat(content).isEmpty();
        }
    }

    @Nested
    @DisplayName("Budget Enforcement with Adaptive Footprint")
    class BudgetEnforcementTest {

        @Test
        @DisplayName("canon memory unit estimated with fewer tokens than provisional for same text")
        void canonFewerTokensThanProvisional() {
            var enforcer = new PromptBudgetEnforcer();
            var counter = new TestTokenCounter();

            var provisionalUnit = testUnit("test fact", Authority.PROVISIONAL, 500);
            var canonUnit = testUnit("test fact", Authority.CANON, 500);

            var provisionalTokens = estimateUnitTokens(enforcer, counter, provisionalUnit, true);
            var canonTokens = estimateUnitTokens(enforcer, counter, canonUnit, true);

            assertThat(canonTokens).isLessThan(provisionalTokens);
        }

        @Test
        @DisplayName("disabled mode uses uniform estimation")
        void disabledModeUniformEstimation() {
            var enforcer = new PromptBudgetEnforcer();
            var counter = new TestTokenCounter();

            var provisionalUnit = testUnit("test fact", Authority.PROVISIONAL, 500);
            var canonUnit = testUnit("test fact", Authority.CANON, 500);

            var provisionalTokens = estimateUnitTokens(enforcer, counter, provisionalUnit, false);
            var canonTokens = estimateUnitTokens(enforcer, counter, canonUnit, false);

            assertThat(canonTokens).isEqualTo(provisionalTokens);
        }

        @Test
        @DisplayName("adaptive enabled allows more memory units in same budget")
        void adaptiveAllowsMoreUnits() {
            var enforcer = new PromptBudgetEnforcer();
            var counter = new TestTokenCounter();
            var compliance = mockCompliancePolicy();

            var units = List.of(
                    testUnit("fact1", Authority.CANON, 500),
                    testUnit("fact2", Authority.CANON, 400),
                    testUnit("fact3", Authority.RELIABLE, 600),
                    testUnit("fact4", Authority.UNRELIABLE, 350),
                    testUnit("fact5", Authority.PROVISIONAL, 200));

            var disabledResult = enforcer.enforce(units, 1000, counter, compliance, false);
            var enabledResult = enforcer.enforce(units, 1000, counter, compliance, true);

            assertThat(enabledResult.included().size()).isGreaterThanOrEqualTo(disabledResult.included().size());
        }

        @Test
        @DisplayName("enforce preserves CANON memory units even over budget")
        void preserveCanonOverBudget() {
            var enforcer = new PromptBudgetEnforcer();
            var counter = new TestTokenCounter();
            var compliance = mockCompliancePolicy();

            var units = List.of(
                    testUnit("canon1", Authority.CANON, 800),
                    testUnit("canon2", Authority.CANON, 700),
                    testUnit("reliable", Authority.RELIABLE, 600),
                    testUnit("prov1", Authority.PROVISIONAL, 300));

            var result = enforcer.enforce(units, 100, counter, compliance, true);

            var canonIncluded = result.included().stream()
                    .filter(a -> a.authority() == Authority.CANON)
                    .count();
            assertThat(canonIncluded).isEqualTo(2L);
            assertThat(result.budgetExceeded()).isTrue();
        }
    }

    // Helper methods

    private MemoryUnit testUnit(String text, Authority authority, int rank) {
        return MemoryUnit.withoutTrust(UUID.randomUUID().toString(), text, rank, authority, false, 0.0, 0);
    }

    private int estimateUnitTokens(PromptBudgetEnforcer enforcer, TokenCounter counter, MemoryUnit unit, boolean adaptive) {
        try {
            var method = PromptBudgetEnforcer.class.getDeclaredMethod(
                    "estimateUnitTokens", TokenCounter.class, MemoryUnit.class, boolean.class);
            method.setAccessible(true);
            return (int) method.invoke(enforcer, counter, unit, adaptive);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private CompliancePolicy mockCompliancePolicy() {
        var policy = Mockito.mock(CompliancePolicy.class);
        Mockito.when(policy.getStrengthFor(Authority.CANON)).thenReturn(ComplianceStrength.STRICT);
        Mockito.when(policy.getStrengthFor(Authority.RELIABLE)).thenReturn(ComplianceStrength.MODERATE);
        Mockito.when(policy.getStrengthFor(Authority.UNRELIABLE)).thenReturn(ComplianceStrength.PERMISSIVE);
        Mockito.when(policy.getStrengthFor(Authority.PROVISIONAL)).thenReturn(ComplianceStrength.PERMISSIVE);
        return policy;
    }

    private dev.arcmem.core.memory.engine.ArcMemEngine mockArcMemEngine(List<MemoryUnit> units) {
        var engine = Mockito.mock(dev.arcmem.core.memory.engine.ArcMemEngine.class);
        Mockito.when(engine.inject(Mockito.anyString())).thenReturn(units);
        return engine;
    }

    private static class TestTokenCounter implements TokenCounter {
        @Override
        public int estimate(String text) {
            if (text == null || text.isEmpty()) {
                return 0;
            }
            var words = text.split("\\s+").length;
            var chars = text.length();
            return words + (chars / 4);
        }
    }
}
