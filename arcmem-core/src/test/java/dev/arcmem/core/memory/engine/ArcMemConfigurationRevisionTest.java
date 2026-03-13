package dev.arcmem.core.memory.engine;
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
import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.spi.llm.LlmCallService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("ArcMemConfiguration revision config")
class ArcMemConfigurationRevisionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ArcMemConfiguration.class)
            .withBean(ChatModel.class, () -> mock(ChatModel.class))
            .withBean(LlmCallService.class, () -> mock(LlmCallService.class))
            .withBean(MemoryUnitMutationStrategy.class, HitlOnlyMutationStrategy::new)
            .withBean(MemoryPressureGauge.class, () -> mock(MemoryPressureGauge.class))
            .withBean(ArcMemEngine.class, () -> mock(ArcMemEngine.class))
            .withBean(MemoryUnitRepository.class, () -> mock(MemoryUnitRepository.class))
            .withBean(CanonizationGate.class, () -> mock(CanonizationGate.class))
            .withBean(InvariantEvaluator.class, () -> mock(InvariantEvaluator.class))
            .withPropertyValues(
                    "arc-mem.conflict-detection.strategy=llm",
                    "arc-mem.conflict-detection.model=gpt-4o-nano",
                    "arc-mem.conflict.negation-overlap-threshold=0.5",
                    "arc-mem.conflict.llm-confidence=0.9",
                    "arc-mem.conflict.replace-threshold=0.8",
                    "arc-mem.conflict.demote-threshold=0.6",
                    "arc-mem.conflict.tier.hot-defense-modifier=0.1",
                    "arc-mem.conflict.tier.warm-defense-modifier=0.0",
                    "arc-mem.conflict.tier.cold-defense-modifier=-0.1");

    @Test
    @DisplayName("ARC-Mem config defaults revision settings when unset")
    void unitConfigDefaultsRevisionSettingsWhenUnset() {
        var config = new ArcMemProperties.UnitConfig(
                20,
                500,
                100,
                900,
                true,
                0.65,
                DedupStrategy.FAST_THEN_LLM,
                CompliancePolicyMode.TIERED,
                true,
                true,
                true,
                0.6,
                400,
                200,
                null,
                null,
                null,
                null,
                null);

        assertThat(config.revision().enabled()).isTrue();
        assertThat(config.revision().reliableRevisable()).isFalse();
        assertThat(config.revision().confidenceThreshold()).isEqualTo(0.75);
    }

    @Test
    @DisplayName("enabled revision config builds RevisionAwareConflictResolver")
    void enabledRevisionConfigBuildsRevisionAwareResolver() {
        var configuration = new ArcMemConfiguration(properties(true, false, 0.75));
        var authorityResolver = configuration.authorityConflictResolver();

        var resolver = configuration.revisionAwareConflictResolver(authorityResolver, new HitlOnlyMutationStrategy());

        assertThat(resolver).isInstanceOf(RevisionAwareConflictResolver.class);
    }

    @Test
    @DisplayName("disabled revision config uses authority resolver as primary")
    void disabledRevisionConfigUsesAuthorityResolverAsPrimary() {
        var configuration = new ArcMemConfiguration(properties(false, false, 0.75));
        var authorityResolver = configuration.authorityConflictResolver();

        var resolver = configuration.authorityPrimaryConflictResolver(authorityResolver);

        assertThat(resolver).isSameAs(authorityResolver);
    }

    @Test
    @DisplayName("revision enabled wires RevisionAwareConflictResolver as primary ConflictResolver")
    void revisionEnabledWiresRevisionAwareResolver() {
        contextRunner
                .withPropertyValues("arc-mem.unit.revision.enabled=true")
                .run(context -> {
                    assertThat(context).hasBean("revisionAwareConflictResolver");
                    assertThat(context).doesNotHaveBean("authorityPrimaryConflictResolver");
                    assertThat(context.getBean(ConflictResolver.class))
                            .isInstanceOf(RevisionAwareConflictResolver.class);
                });
    }

    @Test
    @DisplayName("revision disabled wires AuthorityConflictResolver as primary ConflictResolver")
    void revisionDisabledWiresAuthorityResolver() {
        contextRunner
                .withPropertyValues("arc-mem.unit.revision.enabled=false")
                .run(context -> {
                    assertThat(context).hasBean("authorityPrimaryConflictResolver");
                    assertThat(context).doesNotHaveBean("revisionAwareConflictResolver");
                    assertThat(context.getBean(ConflictResolver.class))
                            .isInstanceOf(AuthorityConflictResolver.class);
                });
    }

    private ArcMemProperties properties(boolean enabled, boolean reliableRevisable, double threshold) {
        var unit = new ArcMemProperties.UnitConfig(
                20,
                500,
                100,
                900,
                true,
                0.65,
                DedupStrategy.FAST_THEN_LLM,
                CompliancePolicyMode.TIERED,
                true,
                true,
                true,
                0.6,
                400,
                200,
                null,
                new ArcMemProperties.RevisionConfig(enabled, reliableRevisable, threshold),
                null,
                null,
                null);
        return new ArcMemProperties(
                unit,
                new ArcMemProperties.MemoryConfig(true, null, null, "text-embedding-3-small", 20, 5, 6),
                new ArcMemProperties.PersistenceConfig(false),
                new ArcMemProperties.ConflictDetectionConfig(ConflictStrategy.LLM, "gpt-4o-nano"),
                new ArcMemProperties.AssemblyConfig(0, false, EnforcementStrategy.PROMPT_ONLY),
                new ArcMemProperties.ConflictConfig(0.5, 0.9, 0.8, 0.6,
                        new ArcMemProperties.TierModifierConfig(0.1, 0.0, -0.1)),
                null, null, null, null, null, new ArcMemProperties.LlmCallConfig(30, 10));
    }
}
