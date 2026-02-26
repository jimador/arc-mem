package dev.dunnam.diceanchors.sim.engine;

import java.util.Set;

/**
 * Immutable definition of a drift strategy loaded from the strategy catalog YAML.
 * <p>
 * Invariants:
 * <ul>
 *   <li>DS1: {@code id}, {@code displayName}, {@code description}, and {@code promptGuidance}
 *       are never blank.</li>
 *   <li>DS2: {@code applicableCategories} is never empty and is defensively copied to an
 *       unmodifiable set.</li>
 * </ul>
 *
 * @param id                   unique strategy identifier (e.g. "SUBTLE_REFRAME")
 * @param displayName          human-readable name (e.g. "Subtle Reframe")
 * @param description          what the strategy does
 * @param tier                 difficulty tier
 * @param applicableCategories anchor categories this strategy targets
 * @param multiTurn            whether the strategy spans multiple conversation turns
 * @param promptGuidance       LLM prompt text describing how to execute this strategy
 */
public record DriftStrategyDefinition(
        String id,
        String displayName,
        String description,
        StrategyTier tier,
        Set<String> applicableCategories,
        boolean multiTurn,
        String promptGuidance
) {
    public DriftStrategyDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (tier == null) {
            throw new IllegalArgumentException("tier must not be null");
        }
        if (applicableCategories == null || applicableCategories.isEmpty()) {
            throw new IllegalArgumentException("applicableCategories must not be empty");
        }
        if (promptGuidance == null || promptGuidance.isBlank()) {
            throw new IllegalArgumentException("promptGuidance must not be blank");
        }
        applicableCategories = Set.copyOf(applicableCategories);
    }
}
