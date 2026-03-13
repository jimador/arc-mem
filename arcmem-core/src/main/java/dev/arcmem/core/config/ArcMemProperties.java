package dev.arcmem.core.config;
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

import com.embabel.common.ai.model.LlmOptions;
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

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "arc-mem")
public record ArcMemProperties(
        @Valid @NestedConfigurationProperty UnitConfig unit,
        @NestedConfigurationProperty MemoryConfig memory,
        @NestedConfigurationProperty PersistenceConfig persistence,
        @NestedConfigurationProperty ConflictDetectionConfig conflictDetection,
        @Valid @NestedConfigurationProperty AssemblyConfig assembly,
        @Valid @NestedConfigurationProperty ConflictConfig conflict,
        @Valid @NestedConfigurationProperty RetrievalConfig retrieval,
        @Nullable @NestedConfigurationProperty AttentionConfig attention,
        @NestedConfigurationProperty MaintenanceConfig maintenance,
        @Valid @NestedConfigurationProperty PressureConfig pressure,
        @Nullable @NestedConfigurationProperty TieredStorageConfig tieredStorage,
        @Valid @NestedConfigurationProperty BudgetConfig budget,
        @NestedConfigurationProperty LlmCallConfig llmCall
) {

    public record QualityScoringConfig(
            @DefaultValue("false") boolean enabled
    ) {}

    public record UnitConfig(
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
            @DefaultValue("true") boolean autoApprovePromotions,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.6") double demoteThreshold,
            @DefaultValue("400") int reliableRankThreshold,
            @DefaultValue("200") int unreliableRankThreshold,
            @Valid @NestedConfigurationProperty TierConfig tier,
            @Valid @NestedConfigurationProperty RevisionConfig revision,
            @Nullable InvariantConfig invariants,
            @Nullable ChatSeedConfig chatSeed,
            @Nullable @NestedConfigurationProperty QualityScoringConfig qualityScoring
    ) {
        public UnitConfig {
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
            @Min(0) @DefaultValue("0") int promptTokenBudget,
            @DefaultValue("false") boolean adaptiveFootprintEnabled,
            @DefaultValue("PROMPT_ONLY") EnforcementStrategy enforcementStrategy
    ) {}

    public record LlmCallConfig(
            @Positive @DefaultValue("30") int callTimeoutSeconds,
            @Positive @DefaultValue("10") int batchMaxSize
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

    public record ConflictDetectionConfig(
            @DefaultValue("LLM") ConflictStrategy strategy,
            @DefaultValue("gpt-4o-nano") String model
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
            @Nullable String unitTextPattern,
            @Nullable Authority minimumAuthority,
            @Nullable Integer minimumCount
    ) {}

    public record ChatSeedConfig(
            @DefaultValue("false") boolean enabled,
            @Nullable List<ChatSeedUnit> units
    ) {}

    public record ChatSeedUnit(
            String text,
            @DefaultValue("RELIABLE") Authority authority,
            @DefaultValue("500") int rank,
            @DefaultValue("false") boolean pinned
    ) {}

    public record AttentionConfig(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("PT5M") Duration windowDuration,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.5") double pressureThreshold,
            @Min(1) @DefaultValue("3") int minConflictsForPressure,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.7") double heatPeakThreshold,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.2") double heatDropThreshold,
            @Min(2) @DefaultValue("3") int clusterDriftMinUnits,
            @Positive @DefaultValue("20") int maxExpectedEventsPerWindow
    ) {}

    public record MaintenanceConfig(
            @DefaultValue("REACTIVE") MaintenanceMode mode,
            @Valid @NestedConfigurationProperty ProactiveConfig proactive
    ) {}

    public record ProactiveConfig(
            @Min(1) @DefaultValue("10") int minTurnsBetweenSweeps,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.1") double hardPruneThreshold,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.3") double softPruneThreshold,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.6") double softPrunePressureThreshold,
            @Min(1) @DefaultValue("10") int candidacyMinReinforcements,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.8") double candidacyMinAuditScore,
            @Min(1) @DefaultValue("5") int candidacyMinAge,
            @Min(1) @Max(200) @DefaultValue("50") int rankBoostAmount,
            @Min(1) @Max(200) @DefaultValue("50") int rankPenaltyAmount,
            @DefaultValue("false") boolean llmAuditEnabled,
            @DefaultValue("false") boolean prologPreFilterEnabled
    ) {
        @AssertTrue(message = "hardPruneThreshold must be less than softPruneThreshold")
        public boolean isHardBelowSoft() {
            return hardPruneThreshold < softPruneThreshold;
        }
    }

    /**
     * Configuration for three-tier unit storage (HOT/WARM/COLD).
     * Disabled by default; opt-in via {@code arc-mem.tiered-storage.enabled=true}.
     */
    public record TieredStorageConfig(
            @DefaultValue("false") boolean enabled,
            @Positive @DefaultValue("1000") int maxCacheSize,
            @Positive @DefaultValue("60") int ttlMinutes
    ) {}

    public record PressureConfig(
            @DefaultValue("true") boolean enabled,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.4") double budgetWeight,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.3") double conflictWeight,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.2") double decayWeight,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.1") double compactionWeight,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.4") double lightSweepThreshold,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.8") double fullSweepThreshold,
            @DecimalMin("0.0") @DefaultValue("1.5") double budgetExponent,
            @Min(1) @DefaultValue("5") int conflictWindowSize
    ) {
        @AssertTrue(message = "pressure weights must sum to 1.0 (tolerance 0.001)")
        public boolean isWeightSumValid() {
            return Math.abs(budgetWeight + conflictWeight + decayWeight + compactionWeight - 1.0) <= 0.001;
        }

        @AssertTrue(message = "fullSweepThreshold must be greater than lightSweepThreshold")
        public boolean isFullSweepGreaterThanLight() {
            return fullSweepThreshold > lightSweepThreshold;
        }
    }

    /**
     * Configuration for pluggable budget enforcement strategy.
     * <p>
     * {@code strategy} selects the implementation. Threshold/factor fields apply only
     * when {@code INTERFERENCE_DENSITY} is selected; they are ignored for {@code COUNT}.
     */
    public record BudgetConfig(
            @DefaultValue("COUNT") BudgetStrategyType strategy,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.6") double densityWarningThreshold,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.8") double densityReductionThreshold,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.5") double densityReductionFactor
    ) {}
}
