package dev.dunnam.diceanchors.sim.engine;

/**
 * Runtime toggles for anchor mutation behavior during simulation turns.
 *
 * @param rankMutationEnabled      whether reinforcement rank boosts and dormancy decay are applied
 * @param authorityPromotionEnabled whether automatic authority upgrades are applied on reinforcement
 */
public record SimulationRuntimeConfig(
        boolean rankMutationEnabled,
        boolean authorityPromotionEnabled
) {
    public static SimulationRuntimeConfig fullAnchors() {
        return new SimulationRuntimeConfig(true, true);
    }
}
