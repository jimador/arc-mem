package dev.arcmem.core.assembly.protection;

import dev.arcmem.core.memory.model.Authority;

import java.util.Set;

/**
 * Token-level constraint derived from a single unit proposition.
 * <p>
 * Entity name biasing: capitalized proper nouns extracted from the unit text
 * become {@code boostTokens}. {@code translationCoverage} tracks what fraction
 * of the unit's total tokens translated to expressible constraints — a metric
 * for the gap between logit bias and full semantic enforcement.
 *
 * @param unitId              Neo4j node ID of the source unit
 * @param authority           authority level of the source unit (governs bias strength)
 * @param boostTokens         tokens to boost in the logit bias map (entity names)
 * @param suppressTokens      tokens to suppress (reserved for future negation-aware extraction)
 * @param translationCoverage fraction of unit text tokens that became constraints [0.0, 1.0]
 */
public record SemanticUnitConstraint(
        String unitId,
        Authority authority,
        Set<String> boostTokens,
        Set<String> suppressTokens,
        double translationCoverage
) {}
