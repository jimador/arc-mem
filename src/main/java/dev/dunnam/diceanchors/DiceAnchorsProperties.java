package dev.dunnam.diceanchors;

import com.embabel.common.ai.model.LlmOptions;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.CompliancePolicyMode;
import dev.dunnam.diceanchors.anchor.ConflictStrategy;
import dev.dunnam.diceanchors.anchor.DedupStrategy;
import dev.dunnam.diceanchors.anchor.InvariantRuleType;
import dev.dunnam.diceanchors.anchor.InvariantStrength;
import dev.dunnam.diceanchors.assembly.RetrievalMode;
import dev.dunnam.diceanchors.sim.engine.RunHistoryStoreType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "dice-anchors")
public record DiceAnchorsProperties(
        @Valid @NestedConfigurationProperty AnchorConfig anchor,
        @NestedConfigurationProperty ChatConfig chat,
        @NestedConfigurationProperty MemoryConfig memory,
        @NestedConfigurationProperty PersistenceConfig persistence,
        @NestedConfigurationProperty SimConfig sim,
        @NestedConfigurationProperty ConflictDetectionConfig conflictDetection,
        @NestedConfigurationProperty RunHistoryConfig runHistory,
        @Valid @NestedConfigurationProperty AssemblyConfig assembly,
        @Valid @NestedConfigurationProperty ConflictConfig conflict,
        @Valid @NestedConfigurationProperty RetrievalConfig retrieval
) {

    public record AnchorConfig(
            @Positive @DefaultValue("20") int budget,
            @DefaultValue("500") int initialRank,
            @Min(100) @Max(900) @DefaultValue("100") int minRank,
            @Min(100) @Max(900) @DefaultValue("900") int maxRank,
            @DefaultValue("true") boolean autoActivate,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.65") double autoActivateThreshold,
            @DefaultValue("FAST_THEN_LLM") DedupStrategy dedupStrategy,
            @DefaultValue("TIERED") CompliancePolicyMode compliancePolicy,
            @DefaultValue("true") boolean lifecycleEventsEnabled,
            @DefaultValue("true") boolean canonizationGateEnabled,
            @DefaultValue("true") boolean autoApproveInSimulation,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.6") double demoteThreshold,
            @DefaultValue("400") int reliableRankThreshold,
            @DefaultValue("200") int unreliableRankThreshold,
            @Valid @NestedConfigurationProperty TierConfig tier,
            @Valid @NestedConfigurationProperty RevisionConfig revision,
            @Nullable InvariantConfig invariants,
            @Nullable ChatSeedConfig chatSeed
    ) {
        public AnchorConfig {
            if (revision == null) {
                revision = new RevisionConfig(true, false, 0.75);
            }
        }

        @AssertTrue(message = "minRank must be < maxRank")
        public boolean isRankRangeValid() {
            return minRank < maxRank;
        }

        @AssertTrue(message = "initialRank must be in [minRank, maxRank]")
        public boolean isInitialRankInRange() {
            return initialRank >= minRank && initialRank <= maxRank;
        }
    }

    public record TierConfig(
            @Min(100) @Max(900) @DefaultValue("600") int hotThreshold,
            @Min(100) @Max(900) @DefaultValue("350") int warmThreshold,
            @Positive @DefaultValue("1.5") double hotDecayMultiplier,
            @Positive @DefaultValue("1.0") double warmDecayMultiplier,
            @Positive @DefaultValue("0.6") double coldDecayMultiplier
    ) {
        @AssertTrue(message = "hotThreshold must be > warmThreshold")
        public boolean isHotAboveWarm() {
            return hotThreshold > warmThreshold;
        }
    }

    public record RevisionConfig(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("false") boolean reliableRevisable,
            @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0")
            @DefaultValue("0.75") double confidenceThreshold
    ) {}

    public record AssemblyConfig(
            @Min(0) @DefaultValue("0") int promptTokenBudget
    ) {}

    public record ChatConfig(
            @DefaultValue("dm") String persona,
            @DefaultValue("200") int maxWords,
            @NestedConfigurationProperty LlmOptions chatLlm
    ) {}

    public record MemoryConfig(
            @DefaultValue("true") boolean enabled,
            @NestedConfigurationProperty LlmOptions extractionLlm,
            @NestedConfigurationProperty LlmOptions entityResolutionLlm,
            @DefaultValue("text-embedding-3-small") String embeddingServiceName,
            @DefaultValue("20") int windowSize,
            @DefaultValue("5") int windowOverlap,
            @DefaultValue("6") int triggerInterval
    ) {}

    public record PersistenceConfig(
            @DefaultValue("false") boolean clearOnStart
    ) {}

    public record SimConfig(
            @DefaultValue("gpt-4.1-mini") String evaluatorModel,
            @DefaultValue("30") int adversaryBudget,
            @DefaultValue("30") int llmCallTimeoutSeconds,
            @DefaultValue("10") int batchMaxSize,
            @DefaultValue("true") boolean parallelPostResponse,
            @DefaultValue("4") int benchmarkParallelism
    ) {}

    public record ConflictDetectionConfig(
            @DefaultValue("LLM") ConflictStrategy strategy,
            @DefaultValue("gpt-4o-nano") String model
    ) {}

    public record RunHistoryConfig(
            @DefaultValue("MEMORY") RunHistoryStoreType store
    ) {}

    public record ConflictConfig(
            @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0")
            @DefaultValue("0.5") double negationOverlapThreshold,
            @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0")
            @DefaultValue("0.9") double llmConfidence,
            @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0")
            @DefaultValue("0.8") double replaceThreshold,
            @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0")
            @DefaultValue("0.6") double demoteThreshold,
            @Valid @NestedConfigurationProperty TierModifierConfig tier
    ) {
        @AssertTrue(message = "replaceThreshold must be > demoteThreshold")
        public boolean isReplaceAboveDemote() {
            return replaceThreshold > demoteThreshold;
        }
    }

    public record TierModifierConfig(
            @DecimalMin("-0.5") @DecimalMax("0.5") @DefaultValue("0.1") double hotDefenseModifier,
            @DecimalMin("-0.5") @DecimalMax("0.5") @DefaultValue("0.0") double warmDefenseModifier,
            @DecimalMin("-0.5") @DecimalMax("0.5") @DefaultValue("-0.1") double coldDefenseModifier
    ) {}

    public record RetrievalConfig(
            @DefaultValue("HYBRID") RetrievalMode mode,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.0") double minRelevance,
            @Positive @DefaultValue("5") int baselineTopK,
            @Positive @DefaultValue("5") int toolTopK,
            @Valid @NestedConfigurationProperty ScoringConfig scoring
    ) {}

    public record ScoringConfig(
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.4") double authorityWeight,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.3") double tierWeight,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.3") double confidenceWeight
    ) {
        @AssertTrue(message = "scoring weights must sum to 1.0")
        public boolean isWeightSumValid() {
            return Math.abs(authorityWeight + tierWeight + confidenceWeight - 1.0) <= 0.001;
        }
    }

    public record InvariantConfig(
            @DefaultValue("true") boolean enabled,
            @Nullable List<InvariantRuleDefinition> rules
    ) {}

    public record InvariantRuleDefinition(
            String id,
            InvariantRuleType type,
            @DefaultValue("MUST") InvariantStrength strength,
            @Nullable String contextId,
            @Nullable String anchorTextPattern,
            @Nullable Authority minimumAuthority,
            @Nullable Integer minimumCount
    ) {}

    public record ChatSeedConfig(
            @DefaultValue("false") boolean enabled,
            @Nullable List<ChatSeedAnchor> anchors
    ) {}

    public record ChatSeedAnchor(
            String text,
            @DefaultValue("RELIABLE") Authority authority,
            @DefaultValue("500") int rank,
            @DefaultValue("false") boolean pinned
    ) {}
}
