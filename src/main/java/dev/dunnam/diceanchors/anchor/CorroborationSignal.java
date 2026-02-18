package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.persistence.PropositionNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Trust signal based on how many distinct sources corroborate a proposition.
 * <p>
 * Uses count-based scoring rather than source-type weighting because source type
 * (DM, player, system) cannot be reliably inferred from source IDs alone.
 * <p>
 * Scoring: 1 source = 0.3, 2 sources = 0.6, 3+ sources = 0.9.
 * Returns empty when sourceIds information is not available.
 */
@Component
public class CorroborationSignal implements TrustSignal {

    @Override
    public String name() {
        return "corroboration";
    }

    @Override
    public OptionalDouble evaluate(PropositionNode proposition, String contextId) {
        List<String> sourceIds = proposition.getSourceIds();
        if (sourceIds == null || sourceIds.isEmpty()) {
            return OptionalDouble.empty();
        }

        long distinctCount = sourceIds.stream().distinct().count();
        if (distinctCount >= 3) {
            return OptionalDouble.of(0.9);
        } else if (distinctCount == 2) {
            return OptionalDouble.of(0.6);
        } else {
            return OptionalDouble.of(0.3);
        }
    }
}
