package dev.dunnam.diceanchors;

import com.embabel.common.ai.model.LlmOptions;
import dev.dunnam.diceanchors.assembly.RetrievalMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "dice-anchors")
public record DiceAnchorsProperties(
        @NestedConfigurationProperty AnchorConfig anchor,
        @NestedConfigurationProperty ChatConfig chat,
        @NestedConfigurationProperty MemoryConfig memory,
        @NestedConfigurationProperty PersistenceConfig persistence,
        @NestedConfigurationProperty SimConfig sim,
        @NestedConfigurationProperty ConflictDetectionConfig conflictDetection,
        @NestedConfigurationProperty RunHistoryConfig runHistory,
        @NestedConfigurationProperty AssemblyConfig assembly,
        @NestedConfigurationProperty ConflictConfig conflict,
        @NestedConfigurationProperty RetrievalConfig retrieval
) {

    public record AnchorConfig(
            @DefaultValue("20") int budget,
            @DefaultValue("500") int initialRank,
            @DefaultValue("100") int minRank,
            @DefaultValue("900") int maxRank,
            @DefaultValue("true") boolean autoActivate,
            @DefaultValue("0.65") double autoActivateThreshold,
            @DefaultValue("FAST_THEN_LLM") String dedupStrategy,
            @DefaultValue("TIERED") String compliancePolicy,
            @DefaultValue("true") boolean lifecycleEventsEnabled,
            @DefaultValue("true") boolean canonizationGateEnabled,
            @DefaultValue("true") boolean autoApproveInSimulation,
            @DefaultValue("0.6") double demoteThreshold,
            @DefaultValue("400") int reliableRankThreshold,
            @DefaultValue("200") int unreliableRankThreshold,
            @NestedConfigurationProperty TierConfig tier
    ) {}

    public record TierConfig(
            @DefaultValue("600") int hotThreshold,
            @DefaultValue("350") int warmThreshold,
            @DefaultValue("1.5") double hotDecayMultiplier,
            @DefaultValue("1.0") double warmDecayMultiplier,
            @DefaultValue("0.6") double coldDecayMultiplier
    ) {}

    public record AssemblyConfig(
            @DefaultValue("0") int promptTokenBudget
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
            @DefaultValue("true") boolean parallelPostResponse
    ) {}

    public record ConflictDetectionConfig(
            @DefaultValue("llm") String strategy,
            @DefaultValue("gpt-4o-nano") String model
    ) {}

    public record RunHistoryConfig(
            @DefaultValue("memory") String store
    ) {}

    public record ConflictConfig(
            @DefaultValue("0.5") double negationOverlapThreshold,
            @DefaultValue("0.9") double llmConfidence,
            @DefaultValue("0.8") double replaceThreshold,
            @DefaultValue("0.6") double demoteThreshold,
            @NestedConfigurationProperty TierModifierConfig tier
    ) {}

    public record TierModifierConfig(
            @DefaultValue("0.1") double hotDefenseModifier,
            @DefaultValue("0.0") double warmDefenseModifier,
            @DefaultValue("-0.1") double coldDefenseModifier
    ) {}

    public record RetrievalConfig(
            @DefaultValue("HYBRID") RetrievalMode mode,
            @DefaultValue("0.0") double minRelevance,
            @DefaultValue("5") int baselineTopK,
            @DefaultValue("5") int toolTopK,
            @NestedConfigurationProperty ScoringConfig scoring
    ) {}

    public record ScoringConfig(
            @DefaultValue("0.4") double authorityWeight,
            @DefaultValue("0.3") double tierWeight,
            @DefaultValue("0.3") double confidenceWeight
    ) {}
}
