package dev.arcmem.simulator.engine;
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

import dev.arcmem.core.spi.llm.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

/**
 * Runtime toggles for unit mutation behavior during simulation turns.
 *
 * @param rankMutationEnabled       whether reinforcement rank boosts and dormancy decay are applied
 * @param authorityPromotionEnabled whether automatic authority upgrades are applied on reinforcement
 */
public record SimulationRuntimeConfig(
        boolean rankMutationEnabled,
        boolean authorityPromotionEnabled
) {
    public static SimulationRuntimeConfig fullUnits() {
        return new SimulationRuntimeConfig(true, true);
    }
}
