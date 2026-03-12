package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Authority;

import java.util.Set;

/**
 * Token-level constraint derived from a single anchor proposition.
 * <p>
 * Entity name biasing: capitalized proper nouns extracted from the anchor text
 * become {@code boostTokens}. {@code translationCoverage} tracks what fraction
 * of the anchor's total tokens translated to expressible constraints — a metric
 * for the gap between logit bias and full semantic enforcement.
 *
 * @param anchorId           Neo4j node ID of the source anchor
 * @param authority          authority level of the source anchor (governs bias strength)
 * @param boostTokens        tokens to boost in the logit bias map (entity names)
 * @param suppressTokens     tokens to suppress (reserved for future negation-aware extraction)
 * @param translationCoverage fraction of anchor text tokens that became constraints [0.0, 1.0]
 */
public record AnchorConstraint(
        String anchorId,
        Authority authority,
        Set<String> boostTokens,
        Set<String> suppressTokens,
        double translationCoverage
) {}
