package dev.arcmem.core.memory.mutation;
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

/**
 * Strategy for determining how an unit's rank and authority respond to reinforcement signals.
 * <p>
 * Implementations are invoked by the unit engine whenever a reinforcement event
 * is applied to an active unit. A single instance MAY be shared across multiple
 * concurrent reinforcement calls; therefore implementations MUST be thread-safe.
 */
public interface ReinforcementPolicy {

    /**
     * Calculate the rank boost to apply when an unit is reinforced.
     *
     * @param unit the unit being reinforced (before the reinforcement is applied)
     * @return the number of rank points to add; MUST be {@code >= 0}
     */
    int calculateRankBoost(MemoryUnit unit);

    /**
     * Determine whether the unit's authority should be upgraded after reinforcement.
     * CANON is never assigned automatically.
     * <p>
     * This method is called after the unit's reinforcement count has been incremented,
     * so the count reflects the post-reinforcement state.
     *
     * @param unit the unit after its reinforcement count has been incremented
     * @return {@code true} if the authority should advance to the next level;
     *         MUST return {@code false} when {@code unit.authority() == Authority.CANON}
     */
    boolean shouldUpgradeAuthority(MemoryUnit unit);

    /**
     * Static factory for the default threshold-based reinforcement policy.
     *
     * @return a {@link ThresholdReinforcementPolicy}
     */
    static ReinforcementPolicy threshold() {
        return new ThresholdReinforcementPolicy();
    }

    /**
     * Boosts rank by a fixed amount per reinforcement and upgrades authority
     * when reinforcement count crosses configured thresholds.
     * <p>
     * PROVISIONAL -&gt; UNRELIABLE at {@value ThresholdReinforcementPolicy#UNRELIABLE_THRESHOLD} reinforcements.<br>
     * UNRELIABLE -&gt; RELIABLE at {@value ThresholdReinforcementPolicy#RELIABLE_THRESHOLD} reinforcements.<br>
     * CANON is never assigned automatically.
     * <p>
     * This class is thread-safe: all fields are final constants.
     */
    class ThresholdReinforcementPolicy implements ReinforcementPolicy {

        static final int RANK_BOOST = 50;
        static final int UNRELIABLE_THRESHOLD = 3;
        static final int RELIABLE_THRESHOLD = 7;

        @Override
        public int calculateRankBoost(MemoryUnit unit) {
            return RANK_BOOST;
        }

        @Override
        public boolean shouldUpgradeAuthority(MemoryUnit unit) {
            if (unit.authority() == Authority.CANON) {
                return false;
            }
            return (unit.authority() == Authority.PROVISIONAL && unit.reinforcementCount() >= UNRELIABLE_THRESHOLD)
                   || (unit.authority() == Authority.UNRELIABLE && unit.reinforcementCount() >= RELIABLE_THRESHOLD);
        }
    }

}
