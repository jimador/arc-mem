package dev.arcmem.simulator.benchmark;

import dev.arcmem.core.memory.model.Authority;
import dev.arcmem.core.memory.model.MemoryUnit;
import dev.arcmem.simulator.scenario.SimulationScenario;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Declarative ablation condition that configures unit subsystem behavior per simulation run.
 * <p>
 * Conditions are applied at the seed-unit level BEFORE the simulation loop starts.
 * They do NOT mutate live unit state mid-run.
 * <p>
 * Seven built-in conditions are provided as static constants:
 * <ul>
 *   <li>{@link #FULL_AWMU} — control condition, all subsystems active</li>
 *   <li>{@link #NO_AWMU} — injection disabled</li>
 *   <li>{@link #FLAT_AUTHORITY} — all seed units set to RELIABLE, promotion disabled</li>
 *   <li>{@link #NO_RANK_DIFFERENTIATION} — all seed units set to rank 500, mutation disabled</li>
 *   <li>{@link #NO_TRUST} — trust pipeline disabled; all propositions pass through at score 0.9</li>
 *   <li>{@link #NO_COMPLIANCE} — compliance enforcement disabled</li>
 *   <li>{@link #NO_LIFECYCLE} — rank mutation, authority promotion, and lifecycle disabled</li>
 * </ul>
 * Custom conditions can be constructed directly via the record constructor.
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li><strong>AC1</strong>: Built-in conditions are immutable.</li>
 *   <li><strong>AC2</strong>: Condition application is idempotent.</li>
 *   <li><strong>AC3</strong>: Conditions do not violate unit invariants A1, A2, A3, A4.</li>
 * </ul>
 *
 * @param name                      unique display name for the condition
 * @param injectionEnabled          whether unit context is injected into LLM prompts
 * @param authorityOverride         if non-null, all seed units receive this authority
 * @param rankOverride              if non-null, all seed units receive this rank (clamped to [100,900])
 * @param rankMutationEnabled       whether rank decay/reinforcement operates during the run
 * @param authorityPromotionEnabled whether automatic authority promotion operates during the run
 * @param trustEnabled              whether the trust pipeline evaluates propositions before promotion
 * @param complianceEnabled         whether compliance enforcement runs on DM responses
 * @param lifecycleEnabled          whether reinforcement, maintenance, and dormancy lifecycle run per turn
 */
public record AblationCondition(
        String name,
        boolean injectionEnabled,
        @Nullable Authority authorityOverride,
        @Nullable Integer rankOverride,
        boolean rankMutationEnabled,
        boolean authorityPromotionEnabled,
        boolean trustEnabled,
        boolean complianceEnabled,
        boolean lifecycleEnabled
) {
    /**
     * Control condition: all ARC Working Memory Unit (AWMU) subsystems active.
     */
    public static final AblationCondition FULL_AWMU = new AblationCondition(
            "FULL_AWMU", true, null, null, true, true, true, true, true);

    /**
     * Ablation: no AWMU context injected into LLM prompts.
     */
    public static final AblationCondition NO_AWMU = new AblationCondition(
            "NO_AWMU", false, null, null, false, false, true, true, true);

    /**
     * Ablation: all seed units set to RELIABLE authority, promotion disabled.
     */
    public static final AblationCondition FLAT_AUTHORITY = new AblationCondition(
            "FLAT_AUTHORITY", true, Authority.RELIABLE, null, true, false, true, true, true);

    /**
     * Ablation: all seed units set to rank 500, rank mutation disabled.
     */
    public static final AblationCondition NO_RANK_DIFFERENTIATION = new AblationCondition(
            "NO_RANK_DIFFERENTIATION", true, null, 500, false, true, true, true, true);

    /**
     * Ablation: trust pipeline disabled; all propositions pass through at score 0.9, zone AUTO_PROMOTE.
     */
    public static final AblationCondition NO_TRUST = new AblationCondition(
            "NO_TRUST", true, null, null, true, true, false, true, true);

    /**
     * Ablation: compliance enforcement disabled; DM responses are never validated post-generation.
     */
    public static final AblationCondition NO_COMPLIANCE = new AblationCondition(
            "NO_COMPLIANCE", true, null, null, true, true, true, false, true);

    /**
     * Ablation: reinforcement, maintenance, and dormancy lifecycle disabled.
     * Seed units retain their natural authority and rank; injection and trust still active.
     */
    public static final AblationCondition NO_LIFECYCLE = new AblationCondition(
            "NO_LIFECYCLE", true, null, null, false, false, true, true, false);

    public AblationCondition {
        Objects.requireNonNull(name, "Condition name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Condition name must not be blank");
        }
        if (rankOverride != null) {
            rankOverride = MemoryUnit.clampRank(rankOverride);
        }
    }

    public List<SimulationScenario.SeedUnit> applySeedUnits(List<SimulationScenario.SeedUnit> seedUnits) {
        if (seedUnits == null || seedUnits.isEmpty()) {
            return List.of();
        }
        return seedUnits.stream()
                          .map(seed -> new SimulationScenario.SeedUnit(
                                  seed.text(),
                                  authorityOverride != null ? authorityOverride.name() : seed.authority(),
                                  rankOverride != null ? rankOverride : seed.rank()
                          ))
                          .toList();
    }
}
