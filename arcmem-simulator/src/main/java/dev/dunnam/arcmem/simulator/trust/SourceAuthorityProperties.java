package dev.dunnam.arcmem.simulator.trust;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "arc-mem.trust.source-authority")
public record SourceAuthorityProperties(
        Map<String, Double> scores,
        double defaultScore
) {
    public SourceAuthorityProperties {
        if (scores == null) {
            scores = Map.of("system", 1.0, "dm", 0.9, "player", 0.3);
        }
        if (defaultScore == 0.0) {
            defaultScore = 0.5;
        }
    }
}
