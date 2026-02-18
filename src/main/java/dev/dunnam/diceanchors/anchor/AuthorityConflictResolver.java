package dev.dunnam.diceanchors.anchor;

/**
 * Resolves anchor conflicts based on authority level.
 * <p>
 * RELIABLE or CANON anchors are defended (KEEP_EXISTING).
 * High-confidence incoming statements can displace PROVISIONAL anchors (REPLACE).
 * All other cases coexist until a human or explicit eviction resolves them.
 */
public class AuthorityConflictResolver implements ConflictResolver {

    @Override
    public Resolution resolve(ConflictDetector.Conflict conflict) {
        if (conflict.existing().authority().isAtLeast(Authority.RELIABLE)) {
            return Resolution.KEEP_EXISTING;
        }
        if (conflict.confidence() > 0.8) {
            return Resolution.REPLACE;
        }
        return Resolution.COEXIST;
    }
}
