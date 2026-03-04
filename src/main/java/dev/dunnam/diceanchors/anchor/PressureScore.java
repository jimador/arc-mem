package dev.dunnam.diceanchors.anchor;

import java.time.Instant;

public record PressureScore(
        double total,
        double budget,
        double conflict,
        double decay,
        double compaction,
        Instant computedAt
) {
    public static PressureScore zero() {
        return new PressureScore(0.0, 0.0, 0.0, 0.0, 0.0, Instant.now());
    }
}
