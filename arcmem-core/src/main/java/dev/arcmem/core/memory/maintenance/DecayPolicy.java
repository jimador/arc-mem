package dev.arcmem.core.memory.maintenance;
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

import dev.arcmem.core.config.ArcMemProperties;

/**
 * Strategy interface for computing memory unit rank decay over time.
 *
 * <h2>Thread safety</h2>
 * Implementations MUST be thread-safe. Multiple threads may call {@link #applyDecay}
 * and {@link #shouldDemoteAuthority} concurrently with different units.
 *
 * <h2>Error handling</h2>
 * Implementations MUST NOT throw from either method. On failure they MUST return
 * a safe default: the unit's current rank for {@link #applyDecay}, and
 * {@code false} for {@link #shouldDemoteAuthority}.
 */
public interface DecayPolicy {

    /**
     * Calculate the new rank after applying decay based on elapsed time.
     * <p>
     * Contract:
     * <ul>
     *   <li>The returned rank MUST be clamped to [{@link MemoryUnit#MIN_RANK}, {@link MemoryUnit#MAX_RANK}].</li>
     *   <li>Pinned memory units MUST NOT have their rank reduced by decay.</li>
     *   <li>This method MUST NOT throw. On error, return {@code unit.rank()} as a safe default.</li>
     * </ul>
     *
     * @param unit                  the unit to decay; never null
     * @param hoursSinceReinforcement hours elapsed since the unit was last reinforced;
     *                                0 means no time has passed (result should equal current rank)
     * @return the new rank, clamped to [{@link MemoryUnit#MIN_RANK}, {@link MemoryUnit#MAX_RANK}]
     */
    int applyDecay(MemoryUnit unit, long hoursSinceReinforcement);

    /**
     * Calculate the new rank after applying tier-modulated decay.
     * <p>
     * The {@code tierMultiplier} scales the effective half-life: values &gt; 1.0 slow decay,
     * values &lt; 1.0 accelerate it. Clamped to a minimum of 0.01 to prevent division by zero.
     *
     * @param unit                  the unit to decay; never null
     * @param hoursSinceReinforcement hours elapsed since last reinforcement
     * @param tierMultiplier          multiplier from the unit's memory tier
     * @return the new rank, clamped to [{@link MemoryUnit#MIN_RANK}, {@link MemoryUnit#MAX_RANK}]
     */
    default int applyDecay(MemoryUnit unit, long hoursSinceReinforcement, double tierMultiplier) {
        return applyDecay(unit, hoursSinceReinforcement);
    }

    /**
     * Returns {@code true} when the unit's decayed rank should trigger an authority demotion.
     * CANON memory units are exempt from automatic decay-based demotion (invariant A3b).
     * PROVISIONAL has no lower threshold.
     *
     * @param unit  the unit after rank decay has been applied
     * @param newRank the new rank after decay
     * @return true if the unit should be demoted based on rank thresholds
     */
    boolean shouldDemoteAuthority(MemoryUnit unit, int newRank);

    /**
     * Static factory for exponential decay policy using default authority thresholds.
     *
     * @param halfLifeHours the halfLife of the decay
     * @return the decay policy
     */
    static DecayPolicy exponential(double halfLifeHours) {
        return new ExponentialDecayPolicy(halfLifeHours, null);
    }

    /**
     * Static factory for exponential decay policy with authority demotion support.
     *
     * @param halfLifeHours the halfLife of the decay
     * @param unitConfig  unit config supplying rank thresholds for demotion checks
     * @return the decay policy
     */
    static DecayPolicy exponential(double halfLifeHours, ArcMemProperties.UnitConfig unitConfig) {
        return new ExponentialDecayPolicy(halfLifeHours, unitConfig);
    }

    /**
     * Applies exponential decay to unit rank based on elapsed time, modulated by the
     * unit's {@code diceDecay} field.
     * <p>
     * Effective half-life formula: {@code effectiveHalfLife = baseHalfLife / max(diceDecay, 0.01)}.
     * <ul>
     *   <li>{@code diceDecay = 0.0} (permanent) — very large effective half-life, negligible decay.</li>
     *   <li>{@code diceDecay = 1.0} (standard) — standard rate, backward-compatible.</li>
     *   <li>{@code diceDecay > 1.0} (ephemeral) — shorter half-life, faster decay.</li>
     * </ul>
     * Pinned memory units are immune to decay.
     * <p>
     * New rank = current rank * 0.5^(hours / effectiveHalfLife), clamped to [MIN_RANK, MAX_RANK].
     */
    record ExponentialDecayPolicy(double halfLifeHours,
                                  ArcMemProperties.UnitConfig unitConfig) implements DecayPolicy {

        @Override
        public int applyDecay(MemoryUnit unit, long hoursSinceReinforcement) {
            return applyDecay(unit, hoursSinceReinforcement, 1.0);
        }

        @Override
        public int applyDecay(MemoryUnit unit, long hoursSinceReinforcement, double tierMultiplier) {
            if (unit.pinned()) {
                return unit.rank();
            }
            var effectiveHalfLife = halfLifeHours / Math.max(unit.diceDecay(), 0.01) * Math.max(tierMultiplier, 0.01);
            var decayFactor = Math.pow(0.5, hoursSinceReinforcement / effectiveHalfLife);
            return MemoryUnit.clampRank((int) (unit.rank() * decayFactor));
        }

        @Override
        public boolean shouldDemoteAuthority(MemoryUnit unit, int newRank) {
            if (unitConfig == null) {
                return false;
            }
            return switch (unit.authority()) {
                case CANON, PROVISIONAL -> false;
                case RELIABLE -> newRank < unitConfig.reliableRankThreshold();
                case UNRELIABLE -> newRank < unitConfig.unreliableRankThreshold();
            };
        }
    }

}
