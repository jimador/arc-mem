package dev.arcmem.simulator.benchmark;
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
 * Four built-in conditions are provided as static constants:
 * <ul>
 *   <li>{@link #FULL_UNITS} — control condition, all subsystems active</li>
 *   <li>{@link #NO_UNITS} — injection disabled</li>
 *   <li>{@link #FLAT_AUTHORITY} — all seed units set to RELIABLE, promotion disabled</li>
 *   <li>{@link #NO_RANK_DIFFERENTIATION} — all seed units set to rank 500, mutation disabled</li>
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
 */
public record AblationCondition(
        String name,
        boolean injectionEnabled,
        @Nullable Authority authorityOverride,
        @Nullable Integer rankOverride,
        boolean rankMutationEnabled,
        boolean authorityPromotionEnabled
) {
    /**
     * Control condition: all unit subsystems active.
     */
    public static final AblationCondition FULL_UNITS = new AblationCondition(
            "FULL_UNITS", true, null, null, true, true);

    /**
     * Ablation: no unit context injected into LLM prompts.
     */
    public static final AblationCondition NO_UNITS = new AblationCondition(
            "NO_UNITS", false, null, null, false, false);

    /**
     * Ablation: all seed units set to RELIABLE authority, promotion disabled.
     */
    public static final AblationCondition FLAT_AUTHORITY = new AblationCondition(
            "FLAT_AUTHORITY", true, Authority.RELIABLE, null, true, false);

    /**
     * Ablation: all seed units set to rank 500, rank mutation disabled.
     */
    public static final AblationCondition NO_RANK_DIFFERENTIATION = new AblationCondition(
            "NO_RANK_DIFFERENTIATION", true, null, 500, false, true);

    public AblationCondition {
        Objects.requireNonNull(name, "Condition name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Condition name must not be blank");
        }
        if (rankOverride != null) {
            rankOverride = MemoryUnit.clampRank(rankOverride);
        }
    }

    /**
     * Applies this condition's overrides to a list of seed units, returning a new list
     * with authority and rank overrides applied. The original list is not modified.
     * <p>
     * This method is idempotent (AC2): applying the same condition multiple times
     * produces the same result.
     *
     * @param seedUnits the original seed unit definitions from the scenario
     *
     * @return a new list with condition overrides applied
     */
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
