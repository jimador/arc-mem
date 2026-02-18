package dev.dunnam.diceanchors.anchor;

public interface ConflictResolver {

    enum Resolution {
        KEEP_EXISTING,
        REPLACE,
        COEXIST
    }

    Resolution resolve(ConflictDetector.Conflict conflict);
}
