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

import dev.arcmem.core.config.ArcMemProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InvariantRuleProvider")
class InvariantRuleProviderTest {

    @Nested
    @DisplayName("loading rules from config")
    class LoadingFromConfig {

        @Test
        @DisplayName("loads authority-floor rule from definition")
        void loadsAuthorityFloor() {
            var def = new ArcMemProperties.InvariantRuleDefinition(
                    "af-1", InvariantRuleType.AUTHORITY_FLOOR, InvariantStrength.MUST, null, "safety", Authority.RELIABLE, null);
            var provider = providerWith(List.of(def));

            var rules = provider.rulesForContext("any");

            assertThat(rules).hasSize(1);
            assertThat(rules.getFirst()).isInstanceOf(InvariantRule.AuthorityFloor.class);
            var floor = (InvariantRule.AuthorityFloor) rules.getFirst();
            assertThat(floor.id()).isEqualTo("af-1");
            assertThat(floor.strength()).isEqualTo(InvariantStrength.MUST);
            assertThat(floor.unitTextPattern()).isEqualTo("safety");
            assertThat(floor.minimumAuthority()).isEqualTo(Authority.RELIABLE);
        }

        @Test
        @DisplayName("loads eviction-immunity rule from definition")
        void loadsEvictionImmunity() {
            var def = new ArcMemProperties.InvariantRuleDefinition(
                    "ei-1", InvariantRuleType.EVICTION_IMMUNITY, InvariantStrength.SHOULD, null, "core", null, null);
            var provider = providerWith(List.of(def));

            var rules = provider.rulesForContext("any");

            assertThat(rules).hasSize(1);
            assertThat(rules.getFirst()).isInstanceOf(InvariantRule.EvictionImmunity.class);
            assertThat(rules.getFirst().strength()).isEqualTo(InvariantStrength.SHOULD);
        }

        @Test
        @DisplayName("loads min-authority-count rule from definition")
        void loadsMinAuthorityCount() {
            var def = new ArcMemProperties.InvariantRuleDefinition(
                    "mac-1", InvariantRuleType.MIN_AUTHORITY_COUNT, InvariantStrength.MUST, null, null, Authority.CANON, 3);
            var provider = providerWith(List.of(def));

            var rules = provider.rulesForContext("any");

            assertThat(rules).hasSize(1);
            assertThat(rules.getFirst()).isInstanceOf(InvariantRule.MinAuthorityCount.class);
            var mac = (InvariantRule.MinAuthorityCount) rules.getFirst();
            assertThat(mac.minimumAuthority()).isEqualTo(Authority.CANON);
            assertThat(mac.minimumCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("loads archive-prohibition rule from definition")
        void loadsArchiveProhibition() {
            var def = new ArcMemProperties.InvariantRuleDefinition(
                    "ap-1", InvariantRuleType.ARCHIVE_PROHIBITION, InvariantStrength.MUST, null, "never-delete", null, null);
            var provider = providerWith(List.of(def));

            var rules = provider.rulesForContext("any");

            assertThat(rules).hasSize(1);
            assertThat(rules.getFirst()).isInstanceOf(InvariantRule.ArchiveProhibition.class);
        }

        @Test
        @DisplayName("null invariants config results in empty rules")
        void nullInvariantsConfig() {
            var props = propertiesWithInvariants(null);
            var provider = new InvariantRuleProvider(props);

            var rules = provider.rulesForContext("any");

            assertThat(rules).isEmpty();
        }

        @Test
        @DisplayName("null rules list in invariant config results in empty rules")
        void nullRulesList() {
            var invariants = new ArcMemProperties.InvariantConfig(true, null);
            var props = propertiesWithInvariants(invariants);
            var provider = new InvariantRuleProvider(props);

            var rules = provider.rulesForContext("any");

            assertThat(rules).isEmpty();
        }
    }

    @Nested
    @DisplayName("context registration and deregistration")
    class ContextRegistration {

        @Test
        @DisplayName("registerForContext adds context-specific rules")
        void registerAddsContextRules() {
            var provider = providerWith(List.of());
            var ctxRule = new InvariantRule.EvictionImmunity(
                    "ei-ctx", InvariantStrength.MUST, "ctx-1", "important");

            provider.registerForContext("ctx-1", List.of(ctxRule));

            var rules = provider.rulesForContext("ctx-1");
            assertThat(rules).hasSize(1);
            assertThat(rules.getFirst().id()).isEqualTo("ei-ctx");
        }

        @Test
        @DisplayName("deregisterForContext removes context-specific rules")
        void deregisterRemovesContextRules() {
            var provider = providerWith(List.of());
            var ctxRule = new InvariantRule.EvictionImmunity(
                    "ei-ctx", InvariantStrength.MUST, "ctx-1", "important");

            provider.registerForContext("ctx-1", List.of(ctxRule));
            provider.deregisterForContext("ctx-1");

            var rules = provider.rulesForContext("ctx-1");
            assertThat(rules).isEmpty();
        }

        @Test
        @DisplayName("deregister of non-existent context is a no-op")
        void deregisterNonExistentIsNoOp() {
            var provider = providerWith(List.of());

            // Should not throw
            provider.deregisterForContext("nonexistent");

            assertThat(provider.rulesForContext("nonexistent")).isEmpty();
        }
    }

    @Nested
    @DisplayName("rulesForContext merging")
    class RulesForContextMerging {

        @Test
        @DisplayName("returns global + context-specific rules combined")
        void returnsGlobalPlusContextSpecific() {
            var globalDef = new ArcMemProperties.InvariantRuleDefinition(
                    "global-1", InvariantRuleType.EVICTION_IMMUNITY, InvariantStrength.MUST, null, "core", null, null);
            var provider = providerWith(List.of(globalDef));
            var ctxRule = new InvariantRule.ArchiveProhibition(
                    "ctx-1", InvariantStrength.SHOULD, "my-ctx", "temp");

            provider.registerForContext("my-ctx", List.of(ctxRule));

            var rules = provider.rulesForContext("my-ctx");
            assertThat(rules).hasSize(2);
            assertThat(rules.stream().map(InvariantRule::id).toList())
                    .containsExactly("global-1", "ctx-1");
        }

        @Test
        @DisplayName("returns only global rules when no context rules registered")
        void returnsOnlyGlobalWhenNoContextRules() {
            var globalDef = new ArcMemProperties.InvariantRuleDefinition(
                    "global-1", InvariantRuleType.EVICTION_IMMUNITY, InvariantStrength.MUST, null, "core", null, null);
            var provider = providerWith(List.of(globalDef));

            var rules = provider.rulesForContext("unregistered-ctx");
            assertThat(rules).hasSize(1);
            assertThat(rules.getFirst().id()).isEqualTo("global-1");
        }

        @Test
        @DisplayName("returned list is immutable")
        void returnedListIsImmutable() {
            var provider = providerWith(List.of());
            var rules = provider.rulesForContext("any");

            assertThatThrownBy(() -> rules.add(
                    new InvariantRule.EvictionImmunity("x", InvariantStrength.MUST, null, "y")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    private static InvariantRuleProvider providerWith(List<ArcMemProperties.InvariantRuleDefinition> defs) {
        var invariants = new ArcMemProperties.InvariantConfig(true, defs);
        var props = propertiesWithInvariants(invariants);
        return new InvariantRuleProvider(props);
    }

    private static ArcMemProperties propertiesWithInvariants(
            ArcMemProperties.InvariantConfig invariants) {
        var unitConfig = new ArcMemProperties.UnitConfig(
                20, 500, 100, 900, true, 0.65,
                DedupStrategy.FAST_THEN_LLM, CompliancePolicyMode.TIERED,
                true, true, true,
                0.6, 400, 200, null, null, invariants, null, null);
        return new ArcMemProperties(
                unitConfig,
                new ArcMemProperties.MemoryConfig(true, null, null, "text-embedding-3-small", 20, 5, 2),
                new ArcMemProperties.PersistenceConfig(false),
                new ArcMemProperties.ConflictDetectionConfig(ConflictStrategy.LLM, "gpt-4o-nano"),
                new ArcMemProperties.AssemblyConfig(0, false, dev.arcmem.core.assembly.compliance.EnforcementStrategy.PROMPT_ONLY),
                null, null, null, null, null, null, null, new ArcMemProperties.LlmCallConfig(30, 10));
    }
}
