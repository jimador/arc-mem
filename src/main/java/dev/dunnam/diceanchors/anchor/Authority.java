package dev.dunnam.diceanchors.anchor;

/**
 * Authority level assigned to an anchor, reflecting how well-established the proposition is.
 * <p>
 * Authority forms a total order: PROVISIONAL < UNRELIABLE < RELIABLE < CANON.
 * The compliance mapping governs how strongly the LLM must respect each level:
 * <ul>
 *   <li><strong>CANON</strong> — MUST (RFC 2119). Immutable ground truth. Never auto-assigned;
 *       requires explicit action (seed anchors, manual tool call, or approved canonization
 *       request). Immune to automatic demotion (invariant A3b).</li>
 *   <li><strong>RELIABLE</strong> — SHOULD. Well-supported by multiple consistent signals.</li>
 *   <li><strong>UNRELIABLE</strong> — MAY. Tentatively accepted; needs more evidence.</li>
 *   <li><strong>PROVISIONAL</strong> — Unverified. Just promoted, confidence uncertain.</li>
 * </ul>
 *
 * <p>Relationship to trust scores: {@link TrustScore#authorityCeiling()} constrains the
 * maximum authority level that can be assigned during promotion or re-evaluation.
 * An anchor cannot be promoted above its trust ceiling.
 *
 * <h2>Invariants (A3a–A3e — replace old A3 "upgrade-only")</h2>
 * <ul>
 *   <li><strong>A3a</strong>: CANON is never assigned by automatic promotion. Only explicit
 *       action (seed anchors, manual tool call, or approved canonization request) can set
 *       CANON.</li>
 *   <li><strong>A3b</strong>: CANON anchors are immune to automatic demotion (decay, trust
 *       re-evaluation). Only explicit action (conflict resolution DEMOTE_EXISTING, manual
 *       tool call, or approved decanonization request) can demote CANON.</li>
 *   <li><strong>A3c</strong>: Automatic demotion (via decay or trust re-evaluation) applies
 *       to RELIABLE → UNRELIABLE → PROVISIONAL.</li>
 *   <li><strong>A3d</strong>: Pinned anchors are immune to automatic demotion. Explicit
 *       demotion still works.</li>
 *   <li><strong>A3e</strong>: All authority transitions (both directions) publish
 *       {@code AuthorityChanged} lifecycle events.</li>
 * </ul>
 *
 * <p>These invariants replace the old single invariant A3 ("authority only upgrades").
 * Authority is now bidirectional: anchors may be promoted upward or demoted downward
 * based on evidence, trust re-evaluation, or explicit action.
 */
public enum Authority {
    PROVISIONAL(0),
    UNRELIABLE(1),
    RELIABLE(2),
    CANON(3);

    private final int level;

    Authority(int level) {
        this.level = level;
    }

    /** Returns the numeric level of this authority (0=PROVISIONAL, 3=CANON). */
    public int level() {
        return level;
    }

    /** Returns {@code true} if this authority is at least as strong as {@code other}. */
    public boolean isAtLeast(Authority other) {
        return this.level >= other.level;
    }

    /**
     * Returns the authority level one step below this one, symmetric with the promotion path.
     * <p>
     * Demotion ladder: CANON → RELIABLE → UNRELIABLE → PROVISIONAL → PROVISIONAL (floor).
     * PROVISIONAL is the floor — calling {@code previousLevel()} on it returns PROVISIONAL.
     *
     * @return the previous (lower) authority level, or PROVISIONAL if already at the floor
     */
    public Authority previousLevel() {
        return switch (this) {
            case CANON -> RELIABLE;
            case RELIABLE -> UNRELIABLE;
            case UNRELIABLE, PROVISIONAL -> PROVISIONAL;
        };
    }
}
