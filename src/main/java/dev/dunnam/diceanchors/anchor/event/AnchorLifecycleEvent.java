package dev.dunnam.diceanchors.anchor.event;

import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.AuthorityChangeDirection;
import dev.dunnam.diceanchors.anchor.ConflictResolver;
import dev.dunnam.diceanchors.anchor.InvariantStrength;
import dev.dunnam.diceanchors.anchor.InvariantViolationData;
import dev.dunnam.diceanchors.anchor.MemoryTier;
import dev.dunnam.diceanchors.anchor.ProposedAction;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.List;

/**
 * Sealed base for all anchor lifecycle events.
 * <p>
 * Each concrete subtype is a static nested class; use the static factory methods
 * ({@link #promoted}, {@link #reinforced}, etc.) to construct events rather than
 * the nested constructors directly.
 *
 * <h2>Event types</h2>
 * <ul>
 *   <li>{@link Promoted} — a proposition was promoted to anchor status</li>
 *   <li>{@link Reinforced} — an anchor's rank was boosted via reinforcement</li>
 *   <li>{@link Archived} — an anchor was deactivated</li>
 *   <li>{@link Evicted} — an anchor was removed from the budget by eviction</li>
 *   <li>{@link ConflictDetected} — incoming text conflicts with active anchors</li>
 *   <li>{@link ConflictResolved} — a conflict was resolved</li>
 *   <li>{@link AuthorityChanged} — an anchor's authority was promoted or demoted</li>
 *   <li>{@link TierChanged} — an anchor's memory tier changed due to rank modification</li>
 *   <li>{@link Superseded} — one anchor superseded another (F04)</li>
 * </ul>
 */
public abstract sealed class AnchorLifecycleEvent extends ApplicationEvent
        permits AnchorLifecycleEvent.Promoted,
                AnchorLifecycleEvent.Reinforced,
                AnchorLifecycleEvent.Archived,
                AnchorLifecycleEvent.Evicted,
                AnchorLifecycleEvent.ConflictDetected,
                AnchorLifecycleEvent.ConflictResolved,
                AnchorLifecycleEvent.AuthorityChanged,
                AnchorLifecycleEvent.TierChanged,
                AnchorLifecycleEvent.Superseded,
                AnchorLifecycleEvent.InvariantViolation {

    private final String contextId;
    private final Instant occurredAt;

    protected AnchorLifecycleEvent(Object source, String contextId) {
        super(source);
        this.contextId = contextId;
        this.occurredAt = Instant.now();
    }

    public String getContextId() { return contextId; }
    public Instant getOccurredAt() { return occurredAt; }

    // ── Static factories ───────────────────────────────────────────────────────

    public static Promoted promoted(Object source, String contextId,
                                    String propositionId, String anchorId, int initialRank) {
        return new Promoted(source, contextId, propositionId, anchorId, initialRank);
    }

    public static Reinforced reinforced(Object source, String contextId,
                                        String anchorId, int previousRank, int newRank,
                                        int reinforcementCount) {
        return new Reinforced(source, contextId, anchorId, previousRank, newRank, reinforcementCount);
    }

    public static Archived archived(Object source, String contextId,
                                    String anchorId, ArchiveReason reason) {
        return new Archived(source, contextId, anchorId, reason);
    }

    public static Evicted evicted(Object source, String contextId,
                                  String anchorId, int previousRank) {
        return new Evicted(source, contextId, anchorId, previousRank);
    }

    public static ConflictDetected conflictDetected(Object source, String contextId,
                                                    String incomingText, int conflictCount,
                                                    List<String> conflictingAnchorIds) {
        return new ConflictDetected(source, contextId, incomingText, conflictCount, conflictingAnchorIds);
    }

    public static ConflictResolved conflictResolved(Object source, String contextId,
                                                    String existingAnchorId,
                                                    ConflictResolver.Resolution resolution) {
        return new ConflictResolved(source, contextId, existingAnchorId, resolution);
    }

    public static TierChanged tierChanged(Object source, String contextId,
                                           String anchorId, MemoryTier previousTier,
                                           MemoryTier newTier) {
        return new TierChanged(source, contextId, anchorId, previousTier, newTier);
    }

    /**
     * Factory for authority change events (both promotions and demotions).
     *
     * @param direction         PROMOTED or DEMOTED
     * @param reason            human-readable reason (e.g., {@code DemotionReason.name()}
     *                          for demotions, or "reinforcement" / "trust-evaluation" for
     *                          promotions)
     */
    public static AuthorityChanged authorityChanged(Object source, String contextId,
                                                    String anchorId, Authority previousAuthority,
                                                    Authority newAuthority,
                                                    AuthorityChangeDirection direction,
                                                    String reason) {
        return new AuthorityChanged(source, contextId, anchorId, previousAuthority, newAuthority,
                direction, reason);
    }

    public static Superseded superseded(Object source, String contextId,
                                        String predecessorId, String successorId,
                                        SupersessionReason reason) {
        return new Superseded(source, contextId, predecessorId, successorId, reason);
    }

    public static InvariantViolation invariantViolation(
            Object source, String contextId,
            String ruleId, InvariantStrength strength,
            ProposedAction blockedAction, String constraintDescription,
            @Nullable String anchorId) {
        return new InvariantViolation(source, contextId, ruleId, strength,
                blockedAction, constraintDescription, anchorId);
    }

    public static InvariantViolation invariantViolation(
            Object source, String contextId, InvariantViolationData violation) {
        return new InvariantViolation(source, contextId, violation.ruleId(),
                violation.strength(), violation.blockedAction(),
                violation.constraintDescription(), violation.anchorId());
    }

    // ── Concrete subtypes ──────────────────────────────────────────────────────

    /** Published when a proposition is promoted to anchor status. */
    public static final class Promoted extends AnchorLifecycleEvent {
        private final String propositionId;
        private final String anchorId;
        private final int initialRank;

        private Promoted(Object source, String contextId,
                         String propositionId, String anchorId, int initialRank) {
            super(source, contextId);
            this.propositionId = propositionId;
            this.anchorId = anchorId;
            this.initialRank = initialRank;
        }

        public String getPropositionId() { return propositionId; }
        public String getAnchorId() { return anchorId; }
        public int getInitialRank() { return initialRank; }
    }

    /** Published when an existing anchor's rank is boosted via reinforcement. */
    public static final class Reinforced extends AnchorLifecycleEvent {
        private final String anchorId;
        private final int previousRank;
        private final int newRank;
        private final int reinforcementCount;

        private Reinforced(Object source, String contextId,
                           String anchorId, int previousRank, int newRank, int reinforcementCount) {
            super(source, contextId);
            this.anchorId = anchorId;
            this.previousRank = previousRank;
            this.newRank = newRank;
            this.reinforcementCount = reinforcementCount;
        }

        public String getAnchorId() { return anchorId; }
        public int getPreviousRank() { return previousRank; }
        public int getNewRank() { return newRank; }
        public int getReinforcementCount() { return reinforcementCount; }
    }

    /** Published when an anchor is archived (deactivated). */
    public static final class Archived extends AnchorLifecycleEvent {
        private final String anchorId;
        private final ArchiveReason reason;

        private Archived(Object source, String contextId, String anchorId, ArchiveReason reason) {
            super(source, contextId);
            this.anchorId = anchorId;
            this.reason = reason;
        }

        public String getAnchorId() { return anchorId; }
        public ArchiveReason getReason() { return reason; }
    }

    /**
     * Published when an anchor is removed from the budget by eviction.
     * Eviction occurs during {@code AnchorEngine.promote()} when the active count
     * exceeds the configured budget. The lowest-ranked non-pinned anchor is evicted.
     * This event fires after the {@link Promoted} event for the incoming anchor.
     */
    public static final class Evicted extends AnchorLifecycleEvent {
        private final String anchorId;
        private final int previousRank;

        private Evicted(Object source, String contextId, String anchorId, int previousRank) {
            super(source, contextId);
            this.anchorId = anchorId;
            this.previousRank = previousRank;
        }

        public String getAnchorId() { return anchorId; }
        public int getPreviousRank() { return previousRank; }
    }

    /** Published when incoming text conflicts with one or more active anchors. */
    public static final class ConflictDetected extends AnchorLifecycleEvent {
        private final String incomingText;
        private final int conflictCount;
        private final List<String> conflictingAnchorIds;

        private ConflictDetected(Object source, String contextId,
                                 String incomingText, int conflictCount,
                                 List<String> conflictingAnchorIds) {
            super(source, contextId);
            this.incomingText = incomingText;
            this.conflictCount = conflictCount;
            this.conflictingAnchorIds = List.copyOf(conflictingAnchorIds);
        }

        public String getIncomingText() { return incomingText; }
        public int getConflictCount() { return conflictCount; }
        public List<String> getConflictingAnchorIds() { return conflictingAnchorIds; }
    }

    /** Published after a conflict between an incoming proposition and an existing anchor is resolved. */
    public static final class ConflictResolved extends AnchorLifecycleEvent {
        private final String existingAnchorId;
        private final ConflictResolver.Resolution resolution;

        private ConflictResolved(Object source, String contextId,
                                 String existingAnchorId, ConflictResolver.Resolution resolution) {
            super(source, contextId);
            this.existingAnchorId = existingAnchorId;
            this.resolution = resolution;
        }

        public String getExistingAnchorId() { return existingAnchorId; }
        public ConflictResolver.Resolution getResolution() { return resolution; }
    }

    /**
     * Published when an anchor's authority level changes — either promoted (upward)
     * or demoted (downward).
     *
     * <p>All authority transitions, in both directions, publish this event (invariant A3e).
     *
     * <p>For promotions, {@code reason} is typically {@code "reinforcement"} or
     * {@code "trust-evaluation"}. For demotions, {@code reason} is the
     * {@link dev.dunnam.diceanchors.anchor.DemotionReason#name()} string.
     */
    public static final class AuthorityChanged extends AnchorLifecycleEvent {
        private final String anchorId;
        private final Authority previousAuthority;
        private final Authority newAuthority;
        private final AuthorityChangeDirection direction;
        private final String reason;

        private AuthorityChanged(Object source, String contextId,
                                  String anchorId, Authority previousAuthority,
                                  Authority newAuthority, AuthorityChangeDirection direction,
                                  String reason) {
            super(source, contextId);
            this.anchorId = anchorId;
            this.previousAuthority = previousAuthority;
            this.newAuthority = newAuthority;
            this.direction = direction;
            this.reason = reason;
        }

        public String getAnchorId() { return anchorId; }
        public Authority getPreviousAuthority() { return previousAuthority; }
        public Authority getNewAuthority() { return newAuthority; }
        public AuthorityChangeDirection getDirection() { return direction; }
        public String getReason() { return reason; }
    }

    /**
     * Published when an anchor's memory tier changes as a result of a rank-modifying
     * operation (reinforce, decay). Not published on initial promotion (initial assignment
     * is not a transition).
     */
    public static final class TierChanged extends AnchorLifecycleEvent {
        private final String anchorId;
        private final MemoryTier previousTier;
        private final MemoryTier newTier;

        private TierChanged(Object source, String contextId,
                            String anchorId, MemoryTier previousTier, MemoryTier newTier) {
            super(source, contextId);
            this.anchorId = anchorId;
            this.previousTier = previousTier;
            this.newTier = newTier;
        }

        public String getAnchorId() { return anchorId; }
        public MemoryTier getPreviousTier() { return previousTier; }
        public MemoryTier getNewTier() { return newTier; }
    }

    /**
     * Published when one anchor supersedes another (e.g., conflict replacement).
     * The predecessor is archived and the successor takes its place.
     */
    public static final class Superseded extends AnchorLifecycleEvent {
        private final String predecessorId;
        private final String successorId;
        private final SupersessionReason reason;

        private Superseded(Object source, String contextId,
                           String predecessorId, String successorId,
                           SupersessionReason reason) {
            super(source, contextId);
            this.predecessorId = predecessorId;
            this.successorId = successorId;
            this.reason = reason;
        }

        public String getPredecessorId() { return predecessorId; }
        public String getSuccessorId() { return successorId; }
        public SupersessionReason getReason() { return reason; }
    }

    /** Published when an operator invariant rule is violated by a proposed lifecycle action. */
    public static final class InvariantViolation extends AnchorLifecycleEvent {
        private final String ruleId;
        private final InvariantStrength strength;
        private final ProposedAction blockedAction;
        private final String constraintDescription;
        private final @Nullable String anchorId;

        private InvariantViolation(Object source, String contextId,
                                   String ruleId, InvariantStrength strength,
                                   ProposedAction blockedAction,
                                   String constraintDescription,
                                   @Nullable String anchorId) {
            super(source, contextId);
            this.ruleId = ruleId;
            this.strength = strength;
            this.blockedAction = blockedAction;
            this.constraintDescription = constraintDescription;
            this.anchorId = anchorId;
        }

        public String getRuleId() { return ruleId; }
        public InvariantStrength getStrength() { return strength; }
        public ProposedAction getBlockedAction() { return blockedAction; }
        public String getConstraintDescription() { return constraintDescription; }
        public @Nullable String getAnchorId() { return anchorId; }
    }
}
