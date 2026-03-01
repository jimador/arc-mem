package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.CompliancePolicyMode;
import dev.dunnam.diceanchors.anchor.ConflictStrategy;
import dev.dunnam.diceanchors.anchor.DedupStrategy;
import dev.dunnam.diceanchors.sim.engine.LlmCallService;
import dev.dunnam.diceanchors.sim.engine.RunHistoryStoreType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("AnchorConfiguration revision config")
class AnchorConfigurationRevisionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AnchorConfiguration.class)
            .withBean(ChatModel.class, () -> mock(ChatModel.class))
            .withBean(LlmCallService.class, () -> mock(LlmCallService.class))
            .withBean(AnchorMutationStrategy.class, HitlOnlyMutationStrategy::new)
            .withPropertyValues(
                    "dice-anchors.conflict-detection.strategy=llm",
                    "dice-anchors.conflict-detection.model=gpt-4o-nano",
                    "dice-anchors.conflict.negation-overlap-threshold=0.5",
                    "dice-anchors.conflict.llm-confidence=0.9",
                    "dice-anchors.conflict.replace-threshold=0.8",
                    "dice-anchors.conflict.demote-threshold=0.6",
                    "dice-anchors.conflict.tier.hot-defense-modifier=0.1",
                    "dice-anchors.conflict.tier.warm-defense-modifier=0.0",
                    "dice-anchors.conflict.tier.cold-defense-modifier=-0.1");

    @Test
    @DisplayName("anchor config defaults revision settings when unset")
    void anchorConfigDefaultsRevisionSettingsWhenUnset() {
        var config = new DiceAnchorsProperties.AnchorConfig(
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
                null);

        assertThat(config.revision().enabled()).isTrue();
        assertThat(config.revision().reliableRevisable()).isFalse();
        assertThat(config.revision().confidenceThreshold()).isEqualTo(0.75);
    }

    @Test
    @DisplayName("enabled revision config builds RevisionAwareConflictResolver")
    void enabledRevisionConfigBuildsRevisionAwareResolver() {
        var configuration = new AnchorConfiguration(properties(true, false, 0.75));
        var authorityResolver = configuration.authorityConflictResolver();

        var resolver = configuration.revisionAwareConflictResolver(authorityResolver, new HitlOnlyMutationStrategy());

        assertThat(resolver).isInstanceOf(RevisionAwareConflictResolver.class);
    }

    @Test
    @DisplayName("disabled revision config uses authority resolver as primary")
    void disabledRevisionConfigUsesAuthorityResolverAsPrimary() {
        var configuration = new AnchorConfiguration(properties(false, false, 0.75));
        var authorityResolver = configuration.authorityConflictResolver();

        var resolver = configuration.authorityPrimaryConflictResolver(authorityResolver);

        assertThat(resolver).isSameAs(authorityResolver);
    }

    @Test
    @DisplayName("revision enabled wires RevisionAwareConflictResolver as primary ConflictResolver")
    void revisionEnabledWiresRevisionAwareResolver() {
        contextRunner
                .withPropertyValues("dice-anchors.anchor.revision.enabled=true")
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
                .withPropertyValues("dice-anchors.anchor.revision.enabled=false")
                .run(context -> {
                    assertThat(context).hasBean("authorityPrimaryConflictResolver");
                    assertThat(context).doesNotHaveBean("revisionAwareConflictResolver");
                    assertThat(context.getBean(ConflictResolver.class))
                            .isInstanceOf(AuthorityConflictResolver.class);
                });
    }

    private DiceAnchorsProperties properties(boolean enabled, boolean reliableRevisable, double threshold) {
        var anchor = new DiceAnchorsProperties.AnchorConfig(
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
                new DiceAnchorsProperties.RevisionConfig(enabled, reliableRevisable, threshold),
                null,
                null);
        return new DiceAnchorsProperties(
                anchor,
                new DiceAnchorsProperties.ChatConfig("dm", 200, null),
                new DiceAnchorsProperties.MemoryConfig(true, null, null, "text-embedding-3-small", 20, 5, 6),
                new DiceAnchorsProperties.PersistenceConfig(false),
                new DiceAnchorsProperties.SimConfig("gpt-4.1-mini", 30, 30, 10, true, 4),
                new DiceAnchorsProperties.ConflictDetectionConfig(ConflictStrategy.LLM, "gpt-4o-nano"),
                new DiceAnchorsProperties.RunHistoryConfig(RunHistoryStoreType.MEMORY),
                new DiceAnchorsProperties.AssemblyConfig(0),
                new DiceAnchorsProperties.ConflictConfig(0.5, 0.9, 0.8, 0.6,
                        new DiceAnchorsProperties.TierModifierConfig(0.1, 0.0, -0.1)),
                null, null);
    }
}
