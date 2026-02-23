package dev.dunnam.diceanchors.sim.benchmark;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.sim.engine.SimulationScenario;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Declarative ablation condition that configures anchor subsystem behavior per simulation run.
 * <p>
 * Conditions are applied at the seed-anchor level BEFORE the simulation loop starts.
 * They do NOT mutate live anchor state mid-run.
 * <p>
 * Four built-in conditions are provided as static constants:
 * <ul>
 *   <li>{@link #FULL_ANCHORS} — control condition, all subsystems active</li>
 *   <li>{@link #NO_ANCHORS} — injection disabled</li>
 *   <li>{@link #FLAT_AUTHORITY} — all seed anchors set to RELIABLE, promotion disabled</li>
 *   <li>{@link #NO_RANK_DIFFERENTIATION} — all seed anchors set to rank 500, mutation disabled</li>
 * </ul>
 * Custom conditions can be constructed directly via the record constructor.
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li><strong>AC1</strong>: Built-in conditions are immutable.</li>
 *   <li><strong>AC2</strong>: Condition application is idempotent.</li>
 *   <li><strong>AC3</strong>: Conditions do not violate anchor invariants A1, A2, A3, A4.</li>
 * </ul>
 *
 * @param name                     unique display name for the condition
 * @param injectionEnabled         whether anchor context is injected into LLM prompts
 * @param authorityOverride        if non-null, all seed anchors receive this authority
 * @param rankOverride             if non-null, all seed anchors receive this rank (clamped to [100,900])
 * @param rankMutationEnabled      whether rank decay/reinforcement operates during the run
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
    /** Control condition: all anchor subsystems active. */
    public static final AblationCondition FULL_ANCHORS = new AblationCondition(
            "FULL_ANCHORS", true, null, null, true, true);

    /** Ablation: no anchor context injected into LLM prompts. */
    public static final AblationCondition NO_ANCHORS = new AblationCondition(
            "NO_ANCHORS", false, null, null, false, false);

    /** Ablation: all seed anchors set to RELIABLE authority, promotion disabled. */
    public static final AblationCondition FLAT_AUTHORITY = new AblationCondition(
            "FLAT_AUTHORITY", true, Authority.RELIABLE, null, true, false);

    /** Ablation: all seed anchors set to rank 500, rank mutation disabled. */
    public static final AblationCondition NO_RANK_DIFFERENTIATION = new AblationCondition(
            "NO_RANK_DIFFERENTIATION", true, null, 500, false, true);

    public AblationCondition {
        Objects.requireNonNull(name, "Condition name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Condition name must not be blank");
        }
        if (rankOverride != null) {
            rankOverride = Anchor.clampRank(rankOverride);
        }
    }

    /**
     * Applies this condition's overrides to a list of seed anchors, returning a new list
     * with authority and rank overrides applied. The original list is not modified.
     * <p>
     * This method is idempotent (AC2): applying the same condition multiple times
     * produces the same result.
     *
     * @param seedAnchors the original seed anchor definitions from the scenario
     * @return a new list with condition overrides applied
     */
    public List<SimulationScenario.SeedAnchor> applySeedAnchors(List<SimulationScenario.SeedAnchor> seedAnchors) {
        if (seedAnchors == null || seedAnchors.isEmpty()) {
            return List.of();
        }
        return seedAnchors.stream()
                .map(seed -> new SimulationScenario.SeedAnchor(
                        seed.text(),
                        authorityOverride != null ? authorityOverride.name() : seed.authority(),
                        rankOverride != null ? rankOverride : seed.rank()
                ))
                .toList();
    }
}
