package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.persistence.PropositionNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Trust signal based on the source of a proposition.
 * DM-sourced propositions receive higher trust than player-sourced ones.
 * <p>
 * Source determination uses the proposition's sourceIds list. Since
 * PropositionNode stores sourceIds as opaque strings, this signal
 * inspects source ID prefixes to infer the source type:
 * <ul>
 *   <li>"dm" prefix or containing "dm" -> 0.9</li>
 *   <li>"player" prefix or containing "player" -> 0.3</li>
 *   <li>"system" prefix or containing "system" -> 1.0</li>
 *   <li>Unknown/null/empty -> 0.5 (neutral default)</li>
 * </ul>
 */
@Component
public class SourceAuthoritySignal implements TrustSignal {

    @Override
    public String name() {
        return "sourceAuthority";
    }

    @Override
    public OptionalDouble evaluate(PropositionNode proposition, String contextId) {
        List<String> sourceIds = proposition.getSourceIds();
        if (sourceIds == null || sourceIds.isEmpty()) {
            return OptionalDouble.of(0.5);
        }

        double maxScore = 0.5;
        for (var sourceId : sourceIds) {
            var lower = sourceId.toLowerCase();
            if (lower.contains("system")) {
                maxScore = Math.max(maxScore, 1.0);
            } else if (lower.contains("dm")) {
                maxScore = Math.max(maxScore, 0.9);
            } else if (lower.contains("player")) {
                maxScore = Math.max(maxScore, 0.3);
            }
        }
        return OptionalDouble.of(maxScore);
    }
}
