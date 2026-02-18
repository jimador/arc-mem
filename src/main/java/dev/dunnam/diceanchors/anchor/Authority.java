package dev.dunnam.diceanchors.anchor;

public enum Authority {
    PROVISIONAL(0),
    UNRELIABLE(1),
    RELIABLE(2),
    CANON(3);

    private final int level;

    Authority(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public boolean isAtLeast(Authority other) {
        return this.level >= other.level;
    }
}
