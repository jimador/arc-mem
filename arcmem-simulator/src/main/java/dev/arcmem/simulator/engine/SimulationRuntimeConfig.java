package dev.arcmem.simulator.engine;

/**
 * Runtime toggles for unit mutation behavior during simulation turns.
 *
 * @param rankMutationEnabled       whether reinforcement rank boosts and dormancy decay are applied
 * @param authorityPromotionEnabled whether automatic authority upgrades are applied on reinforcement
 * @param trustEnabled              whether the trust pipeline evaluates propositions before promotion
 * @param complianceEnabled         whether compliance enforcement runs on DM responses
 * @param lifecycleEnabled          whether reinforcement, maintenance, and dormancy lifecycle run per turn
 */
public record SimulationRuntimeConfig(
        boolean rankMutationEnabled,
        boolean authorityPromotionEnabled,
        boolean trustEnabled,
        boolean complianceEnabled,
        boolean lifecycleEnabled
) {
    public static SimulationRuntimeConfig fullUnits() {
        return new SimulationRuntimeConfig(true, true, true, true, true);
    }
}
