package dev.arcmem.core.memory.model;
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

import org.jspecify.annotations.Nullable;

/**
 * Immutable in-memory view of a promoted proposition — a DICE proposition that has been
 * elevated to the attention memory layer.
 *
 * <h2>Fields</h2>
 * <dl>
 *   <dt>{@code id}</dt>
 *   <dd>The Neo4j node ID of the underlying {@code PropositionNode}. Units share the
 *       same node as the proposition; {@code rank > 0} signals unit status.</dd>
 *
 *   <dt>{@code text}</dt>
 *   <dd>The proposition statement. Injected verbatim into the LLM system prompt as a
 *       structured constraint.</dd>
 *
 *   <dt>{@code rank}</dt>
 *   <dd>Priority within the unit budget. Higher rank = higher priority = less likely
 *       to be evicted. Always clamped to [{@link #MIN_RANK}, {@link #MAX_RANK}].</dd>
 *
 *   <dt>{@code authority}</dt>
 *   <dd>Trust level: PROVISIONAL → UNRELIABLE → RELIABLE → CANON. Governs how strongly
 *       the LLM must treat this unit as ground truth. See {@link Authority} for the
 *       compliance mapping and invariants A3a–A3e.</dd>
 *
 *   <dt>{@code pinned}</dt>
 *   <dd>When {@code true}, the unit is excluded from automatic eviction and automatic
 *       demotion (invariant A3d). Pinned units can still be explicitly archived or
 *       demoted.</dd>
 *
 *   <dt>{@code confidence}</dt>
 *   <dd>Extraction confidence from DICE (0.0–1.0). Used as an input signal to the trust
 *       pipeline and as a secondary sort key in budget decisions.</dd>
 *
 *   <dt>{@code reinforcementCount}</dt>
 *   <dd>Number of times this unit has been reinforced. Drives authority promotion
 *       thresholds (3× → UNRELIABLE, 7× → RELIABLE) and trust re-evaluation triggers.</dd>
 *
 *   <dt>{@code trustScore}</dt>
 *   <dd>Trust pipeline result, or {@code null} for units created before trust scoring
 *       was active. Carries the authority ceiling that caps automatic promotion.</dd>
 *
 *   <dt>{@code diceImportance}</dt>
 *   <dd>Importance score from the DICE proposition (0.0–1.0, default 0.0). High-importance
 *       units (> 0.7) receive a rank boost during budget eviction priority calculations.
 *       Low-importance units (&lt; 0.3) may be evicted earlier. Default 0.0 has no
 *       effect on existing behavior.</dd>
 *
 *   <dt>{@code diceDecay}</dt>
 *   <dd>Decay rate from the DICE proposition (≥ 0.0, default 1.0). Modulates the
 *       half-life in {@code ExponentialDecayPolicy}:
 *       {@code effectiveHalfLife = baseHalfLife / max(diceDecay, 0.01)}.
 *       0.0 = permanent (no decay), 1.0 = standard rate, > 1.0 = faster decay.
 *       Default 1.0 preserves existing decay behavior.</dd>
 * </dl>
 *
 * <h2>Lifecycle</h2>
 * An unit starts at PROVISIONAL authority after promotion. It may be reinforced
 * (rank and authority increase), decay (rank decreases, possibly triggering authority
 * demotion), be demoted explicitly, or be archived when no longer relevant.
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li><strong>A1</strong>: rank is always in [{@link #MIN_RANK}, {@link #MAX_RANK}].</li>
 *   <li><strong>A2</strong>: authority is never null for an active unit.</li>
 *   <li><strong>A3a–A3e</strong>: see {@link Authority} for the bidirectional authority
 *       lifecycle invariants.</li>
 *   <li><strong>A4</strong>: pinned units are immune to rank-based eviction and
 *       automatic authority demotion.</li>
 *   <li><strong>A5</strong>: trustScore may be null for units created before trust
 *       scoring is active.</li>
 * </ul>
 */
public record MemoryUnit(
        String id,
        String text,
        int rank,
        Authority authority,
        boolean pinned,
        double confidence,
        int reinforcementCount,
        @Nullable TrustScore trustScore,
        double diceImportance,
        double diceDecay,
        MemoryTier memoryTier
) {
    public static final int MIN_RANK = 100;
    public static final int MAX_RANK = 900;

    public static int clampRank(int rank) {
        return Math.max(MIN_RANK, Math.min(MAX_RANK, rank));
    }

    /**
     * Creates an MemoryUnit without a trust score, using default DICE field values and WARM tier.
     * <p>
     * {@code diceImportance} defaults to 0.0 (no priority effect).
     * {@code diceDecay} defaults to 1.0 (standard decay rate, backward-compatible).
     * {@code memoryTier} defaults to {@link MemoryTier#WARM} (baseline tier).
     * <p>
     * Use this factory to minimize churn in code that does not participate
     * in trust evaluation, DICE field population, or tier management.
     */
    public static MemoryUnit withoutTrust(String id, String text, int rank, Authority authority,
                                      boolean pinned, double confidence, int reinforcementCount) {
        return new MemoryUnit(id, text, rank, authority, pinned, confidence, reinforcementCount,
                null, 0.0, 1.0, MemoryTier.WARM);
    }
}
