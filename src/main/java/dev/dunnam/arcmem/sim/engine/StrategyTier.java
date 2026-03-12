package dev.dunnam.diceanchors.sim.engine;

/**
 * Difficulty tier for drift strategies, ordered from least to most sophisticated.
 * <p>
 * Invariant: {@code level} values are monotonically increasing from BASIC (1) to EXPERT (4).
 */
public enum StrategyTier {
    BASIC(1),
    INTERMEDIATE(2),
    ADVANCED(3),
    EXPERT(4);

    private final int level;

    StrategyTier(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public boolean isAtOrBelow(StrategyTier other) {
        return this.level <= other.level;
    }
}
