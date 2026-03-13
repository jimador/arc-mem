package dev.arcmem.core.memory.canon;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvariantEvaluator")
class InvariantEvaluatorTest {

    private static final String CONTEXT_ID = "ctx-eval";

    @Mock
    private InvariantRuleProvider ruleProvider;

    private InvariantEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new InvariantEvaluator(ruleProvider);
    }

    @Nested
    @DisplayName("AuthorityFloor evaluation")
    class AuthorityFloorEvaluation {

        @Test
        @DisplayName("blocks demotion when memory unit authority is at or above floor")
        void blocksDemotionAboveFloor() {
            var rule = new InvariantRule.AuthorityFloor(
                    "af-1", InvariantStrength.MUST, null, "safety", Authority.RELIABLE);
            when(ruleProvider.rulesForContext(CONTEXT_ID)).thenReturn(List.of(rule));

            var unit = MemoryUnit.withoutTrust("a1", "safety protocol", 500, Authority.RELIABLE, false, 0.9, 3);
            var eval = evaluator.evaluate(CONTEXT_ID, ProposedAction.DEMOTE, List.of(unit), unit);

            assertThat(eval.hasBlockingViolation()).isTrue();
            assertThat(eval.violations()).hasSize(1);
            assertThat(eval.violations().getFirst().ruleId()).isEqualTo("af-1");
        }

        @Test
        @DisplayName("does not block when memory unit text does not match pattern")
        void noBlockWhenTextDoesNotMatch() {
            var rule = new InvariantRule.AuthorityFloor(
                    "af-2", InvariantStrength.MUST, null, "safety", Authority.RELIABLE);
            when(ruleProvider.rulesForContext(CONTEXT_ID)).thenReturn(List.of(rule));

            var unit = MemoryUnit.withoutTrust("a1", "weather data", 500, Authority.RELIABLE, false, 0.9, 3);
            var eval = evaluator.evaluate(CONTEXT_ID, ProposedAction.DEMOTE, List.of(unit), unit);

            assertThat(eval.hasBlockingViolation()).isFalse();
            assertThat(eval.violations()).isEmpty();
        }

        @Test
        @DisplayName("does not block non-demotion actions")
        void noBlockForArchiveAction() {
            var rule = new InvariantRule.AuthorityFloor(
                    "af-3", InvariantStrength.MUST, null, "safety", Authority.RELIABLE);
            when(ruleProvider.rulesForContext(CONTEXT_ID)).thenReturn(List.of(rule));

            var unit = MemoryUnit.withoutTrust("a1", "safety protocol", 500, Authority.RELIABLE, false, 0.9, 3);
            var eval = evaluator.evaluate(CONTEXT_ID, ProposedAction.ARCHIVE, List.of(unit), unit);

            assertThat(eval.violations()).isEmpty();
        }

        @Test
        @DisplayName("triggers on AUTHORITY_CHANGE action")
        void triggersOnAuthorityChangeAction() {
            var rule = new InvariantRule.AuthorityFloor(
                    "af-4", InvariantStrength.MUST, null, "safety", Authority.RELIABLE);
            when(ruleProvider.rulesForContext(CONTEXT_ID)).thenReturn(List.of(rule));

            var unit = MemoryUnit.withoutTrust("a1", "safety protocol", 500, Authority.RELIABLE, false, 0.9, 3);
            var eval = evaluator.evaluate(CONTEXT_ID, ProposedAction.AUTHORITY_CHANGE, List.of(unit), unit);

            assertThat(eval.hasBlockingViolation()).isTrue();
        }
    }

    @Nested
    @DisplayName("EvictionImmunity evaluation")
    class EvictionImmunityEvaluation {

        @Test
        @DisplayName("blocks eviction of matching memory unit")
        void blocksEvictionOfMatchingUnit() {
            var rule = new InvariantRule.EvictionImmunity(
                    "ei-1", InvariantStrength.MUST, null, "core rule");
            when(ruleProvider.rulesForContext(CONTEXT_ID)).thenReturn(List.of(rule));

            var unit = MemoryUnit.withoutTrust("a1", "core rule of engagement", 200, Authority.PROVISIONAL, false, 0.8, 1);
            var eval = evaluator.evaluate(CONTEXT_ID, ProposedAction.EVICT, List.of(unit), unit);

            assertThat(eval.hasBlockingViolation()).isTrue();
            assertThat(eval.violations().getFirst().blockedAction()).isEqualTo(ProposedAction.EVICT);
        }

        @Test
        @DisplayName("does not block non-evict actions")
        void noBlockForDemoteAction() {
            var rule = new InvariantRule.EvictionImmunity(
                    "ei-2", InvariantStrength.MUST, null, "core rule");
            when(ruleProvider.rulesForContext(CONTEXT_ID)).thenReturn(List.of(rule));

            var unit = MemoryUnit.withoutTrust("a1", "core rule of engagement", 200, Authority.PROVISIONAL, false, 0.8, 1);
            var eval = evaluator.evaluate(CONTEXT_ID, ProposedAction.DEMOTE, List.of(unit), unit);

            assertThat(eval.violations()).isEmpty();
        }
    }

    @Nested
    @DisplayName("MinAuthorityCount evaluation")
    class MinAuthorityCountEvaluation {

        @Test
        @DisplayName("blocks when removing target would drop below minimum count")
        void blocksWhenCountDropsBelowMinimum() {
            var rule = new InvariantRule.MinAuthorityCount(
                    "mac-1", InvariantStrength.MUST, null, Authority.RELIABLE, 2);
            when(ruleProvider.rulesForContext(CONTEXT_ID)).thenReturn(List.of(rule));

            var a1 = MemoryUnit.withoutTrust("a1", "fact one", 600, Authority.RELIABLE, false, 0.9, 5);
            var a2 = MemoryUnit.withoutTrust("a2", "fact two", 500, Authority.RELIABLE, false, 0.8, 3);
            var units = List.of(a1, a2);

            var eval = evaluator.evaluate(CONTEXT_ID, ProposedAction.ARCHIVE, units, a2);

            assertThat(eval.hasBlockingViolation()).isTrue();
        }

        @Test
        @DisplayName("allows when count remains above minimum after removal")
        void allowsWhenCountRemainsAboveMinimum() {
            var rule = new InvariantRule.MinAuthorityCount(
                    "mac-2", InvariantStrength.MUST, null, Authority.RELIABLE, 1);
            when(ruleProvider.rulesForContext(CONTEXT_ID)).thenReturn(List.of(rule));

            var a1 = MemoryUnit.withoutTrust("a1", "fact one", 600, Authority.RELIABLE, false, 0.9, 5);
            var a2 = MemoryUnit.withoutTrust("a2", "fact two", 500, Authority.RELIABLE, false, 0.8, 3);
            var units = List.of(a1, a2);

            var eval = evaluator.evaluate(CONTEXT_ID, ProposedAction.ARCHIVE, units, a2);

            assertThat(eval.hasBlockingViolation()).isFalse();
            assertThat(eval.violations()).isEmpty();
        }

        @Test
        @DisplayName("does not fire for non-destructive actions")
        void noFireForAuthorityChange() {
            var rule = new InvariantRule.MinAuthorityCount(
                    "mac-3", InvariantStrength.MUST, null, Authority.RELIABLE, 2);
            when(ruleProvider.rulesForContext(CONTEXT_ID)).thenReturn(List.of(rule));

            var a1 = MemoryUnit.withoutTrust("a1", "fact one", 600, Authority.RELIABLE, false, 0.9, 5);
            var eval = evaluator.evaluate(CONTEXT_ID, ProposedAction.AUTHORITY_CHANGE, List.of(a1), a1);

            assertThat(eval.violations()).isEmpty();
        }

        @Test
        @DisplayName("does not fire when target is below minimum authority")
        void noFireWhenTargetBelowMinimumAuthority() {
            var rule = new InvariantRule.MinAuthorityCount(
                    "mac-4", InvariantStrength.MUST, null, Authority.RELIABLE, 2);
            when(ruleProvider.rulesForContext(CONTEXT_ID)).thenReturn(List.of(rule));

            var a1 = MemoryUnit.withoutTrust("a1", "fact one", 600, Authority.RELIABLE, false, 0.9, 5);
            var a2 = MemoryUnit.withoutTrust("a2", "fact two", 500, Authority.RELIABLE, false, 0.8, 3);
            var a3 = MemoryUnit.withoutTrust("a3", "provisional", 300, Authority.PROVISIONAL, false, 0.7, 1);
            var units = List.of(a1, a2, a3);

            // Archiving a3 (PROVISIONAL) should not trigger the RELIABLE count rule
            var eval = evaluator.evaluate(CONTEXT_ID, ProposedAction.ARCHIVE, units, a3);

            assertThat(eval.violations()).isEmpty();
        }
    }

    @Nested
    @DisplayName("ArchiveProhibition evaluation")
    class ArchiveProhibitionEvaluation {

        @Test
        @DisplayName("blocks archive of matching memory unit")
        void blocksArchiveOfMatchingUnit() {
            var rule = new InvariantRule.ArchiveProhibition(
                    "ap-1", InvariantStrength.MUST, null, "permanent");
            when(ruleProvider.rulesForContext(CONTEXT_ID)).thenReturn(List.of(rule));

            var unit = MemoryUnit.withoutTrust("a1", "permanent record", 500, Authority.RELIABLE, false, 0.9, 3);
            var eval = evaluator.evaluate(CONTEXT_ID, ProposedAction.ARCHIVE, List.of(unit), unit);

            assertThat(eval.hasBlockingViolation()).isTrue();
        }

        @Test
        @DisplayName("does not block non-archive actions")
        void noBlockForEvictAction() {
            var rule = new InvariantRule.ArchiveProhibition(
                    "ap-2", InvariantStrength.MUST, null, "permanent");
            when(ruleProvider.rulesForContext(CONTEXT_ID)).thenReturn(List.of(rule));

            var unit = MemoryUnit.withoutTrust("a1", "permanent record", 500, Authority.RELIABLE, false, 0.9, 3);
            var eval = evaluator.evaluate(CONTEXT_ID, ProposedAction.EVICT, List.of(unit), unit);

            assertThat(eval.violations()).isEmpty();
        }
    }

    @Nested
    @DisplayName("context scoping")
    class ContextScoping {

        @Test
        @DisplayName("global rules apply to all contexts")
        void globalRulesApplyToAllContexts() {
            var globalRule = new InvariantRule.EvictionImmunity(
                    "ei-global", InvariantStrength.MUST, null, "core");
            when(ruleProvider.rulesForContext("any-context")).thenReturn(List.of(globalRule));

            var unit = MemoryUnit.withoutTrust("a1", "core principle", 200, Authority.PROVISIONAL, false, 0.8, 1);
            var eval = evaluator.evaluate("any-context", ProposedAction.EVICT, List.of(unit), unit);

            assertThat(eval.hasBlockingViolation()).isTrue();
        }

        @Test
        @DisplayName("context-specific rules only apply to their context")
        void contextSpecificRulesOnlyApplyToTheirContext() {
            var ctxRule = new InvariantRule.EvictionImmunity(
                    "ei-ctx", InvariantStrength.MUST, "ctx-specific", "core");
            // ruleProvider returns the ctx-specific rule but we evaluate against a different context
            when(ruleProvider.rulesForContext("other-context")).thenReturn(List.of(ctxRule));

            var unit = MemoryUnit.withoutTrust("a1", "core principle", 200, Authority.PROVISIONAL, false, 0.8, 1);
            var eval = evaluator.evaluate("other-context", ProposedAction.EVICT, List.of(unit), unit);

            // The rule has contextId="ctx-specific" but we're in "other-context", so it should be skipped
            assertThat(eval.hasBlockingViolation()).isFalse();
        }

        @Test
        @DisplayName("context-specific rule applies when context matches")
        void contextSpecificRuleAppliesWhenContextMatches() {
            var ctxRule = new InvariantRule.EvictionImmunity(
                    "ei-ctx", InvariantStrength.MUST, CONTEXT_ID, "core");
            when(ruleProvider.rulesForContext(CONTEXT_ID)).thenReturn(List.of(ctxRule));

            var unit = MemoryUnit.withoutTrust("a1", "core principle", 200, Authority.PROVISIONAL, false, 0.8, 1);
            var eval = evaluator.evaluate(CONTEXT_ID, ProposedAction.EVICT, List.of(unit), unit);

            assertThat(eval.hasBlockingViolation()).isTrue();
        }
    }

    @Nested
    @DisplayName("MUST vs SHOULD separation")
    class MustVsShouldSeparation {

        @Test
        @DisplayName("MUST violation produces blocking result")
        void mustViolationIsBlocking() {
            var rule = new InvariantRule.ArchiveProhibition(
                    "ap-must", InvariantStrength.MUST, null, "critical");
            when(ruleProvider.rulesForContext(CONTEXT_ID)).thenReturn(List.of(rule));

            var unit = MemoryUnit.withoutTrust("a1", "critical data", 500, Authority.RELIABLE, false, 0.9, 3);
            var eval = evaluator.evaluate(CONTEXT_ID, ProposedAction.ARCHIVE, List.of(unit), unit);

            assertThat(eval.hasBlockingViolation()).isTrue();
            assertThat(eval.hasWarnings()).isFalse();
        }

        @Test
        @DisplayName("SHOULD violation produces warning but not blocking")
        void shouldViolationIsWarningOnly() {
            var rule = new InvariantRule.ArchiveProhibition(
                    "ap-should", InvariantStrength.SHOULD, null, "preferred");
            when(ruleProvider.rulesForContext(CONTEXT_ID)).thenReturn(List.of(rule));

            var unit = MemoryUnit.withoutTrust("a1", "preferred data", 500, Authority.RELIABLE, false, 0.9, 3);
            var eval = evaluator.evaluate(CONTEXT_ID, ProposedAction.ARCHIVE, List.of(unit), unit);

            assertThat(eval.hasBlockingViolation()).isFalse();
            assertThat(eval.hasWarnings()).isTrue();
        }
    }

    @Nested
    @DisplayName("mixed rule evaluation")
    class MixedRuleEvaluation {

        @Test
        @DisplayName("multiple rules with some violated and some not")
        void mixedViolations() {
            var mustRule = new InvariantRule.ArchiveProhibition(
                    "ap-must", InvariantStrength.MUST, null, "critical");
            var shouldRule = new InvariantRule.ArchiveProhibition(
                    "ap-should", InvariantStrength.SHOULD, null, "data");
            var noMatchRule = new InvariantRule.ArchiveProhibition(
                    "ap-nomatch", InvariantStrength.MUST, null, "zzz-nonexistent");
            when(ruleProvider.rulesForContext(CONTEXT_ID)).thenReturn(List.of(mustRule, shouldRule, noMatchRule));

            var unit = MemoryUnit.withoutTrust("a1", "critical data", 500, Authority.RELIABLE, false, 0.9, 3);
            var eval = evaluator.evaluate(CONTEXT_ID, ProposedAction.ARCHIVE, List.of(unit), unit);

            assertThat(eval.hasBlockingViolation()).isTrue();
            assertThat(eval.hasWarnings()).isTrue();
            assertThat(eval.violations()).hasSize(2);
            assertThat(eval.checkedCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("no violations scenarios")
    class NoViolations {

        @Test
        @DisplayName("no violations when action does not match any rule type")
        void noViolationsWhenActionDoesNotMatch() {
            var rule = new InvariantRule.ArchiveProhibition(
                    "ap-1", InvariantStrength.MUST, null, "data");
            when(ruleProvider.rulesForContext(CONTEXT_ID)).thenReturn(List.of(rule));

            var unit = MemoryUnit.withoutTrust("a1", "data record", 500, Authority.RELIABLE, false, 0.9, 3);
            // DEMOTE is not matched by ArchiveProhibition
            var eval = evaluator.evaluate(CONTEXT_ID, ProposedAction.DEMOTE, List.of(unit), unit);

            assertThat(eval.violations()).isEmpty();
            assertThat(eval.hasBlockingViolation()).isFalse();
            assertThat(eval.hasWarnings()).isFalse();
        }

        @Test
        @DisplayName("empty rules returns clean evaluation")
        void emptyRulesReturnsCleanEvaluation() {
            when(ruleProvider.rulesForContext(CONTEXT_ID)).thenReturn(List.of());

            var unit = MemoryUnit.withoutTrust("a1", "some data", 500, Authority.RELIABLE, false, 0.9, 3);
            var eval = evaluator.evaluate(CONTEXT_ID, ProposedAction.ARCHIVE, List.of(unit), unit);

            assertThat(eval.violations()).isEmpty();
            assertThat(eval.checkedCount()).isEqualTo(0);
            assertThat(eval.hasBlockingViolation()).isFalse();
            assertThat(eval.hasWarnings()).isFalse();
        }
    }

    @Nested
    @DisplayName("null target memory unit")
    class NullTargetUnit {

        @Test
        @DisplayName("all rules gracefully handle null target memory unit")
        void allRulesHandleNullTarget() {
            var rules = List.<InvariantRule>of(
                    new InvariantRule.AuthorityFloor("af", InvariantStrength.MUST, null, "p", Authority.RELIABLE),
                    new InvariantRule.EvictionImmunity("ei", InvariantStrength.MUST, null, "p"),
                    new InvariantRule.MinAuthorityCount("mac", InvariantStrength.MUST, null, Authority.RELIABLE, 1),
                    new InvariantRule.ArchiveProhibition("ap", InvariantStrength.MUST, null, "p")
            );
            when(ruleProvider.rulesForContext(CONTEXT_ID)).thenReturn(rules);

            var eval = evaluator.evaluate(CONTEXT_ID, ProposedAction.ARCHIVE, List.of(), null);

            assertThat(eval.violations()).isEmpty();
        }
    }
}
