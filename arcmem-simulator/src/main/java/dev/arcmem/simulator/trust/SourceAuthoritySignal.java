package dev.arcmem.simulator.trust;

import dev.arcmem.core.memory.trust.TrustSignal;
import dev.arcmem.core.persistence.PropositionNode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.OptionalDouble;

@Component
@EnableConfigurationProperties(SourceAuthorityProperties.class)
public class SourceAuthoritySignal implements TrustSignal {

    private final SourceAuthorityProperties properties;

    public SourceAuthoritySignal(SourceAuthorityProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "sourceAuthority";
    }

    @Override
    public OptionalDouble evaluate(PropositionNode proposition, String contextId) {
        List<String> sourceIds = proposition.getSourceIds();
        if (sourceIds == null || sourceIds.isEmpty()) {
            return OptionalDouble.of(properties.defaultScore());
        }
        var maxScore = properties.defaultScore();
        for (var sourceId : sourceIds) {
            var lower = sourceId.toLowerCase();
            for (var entry : properties.scores().entrySet()) {
                if (lower.contains(entry.getKey().toLowerCase())) {
                    maxScore = Math.max(maxScore, entry.getValue());
                }
            }
        }
        return OptionalDouble.of(maxScore);
    }
}
