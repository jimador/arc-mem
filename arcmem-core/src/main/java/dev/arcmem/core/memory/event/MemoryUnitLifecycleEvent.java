package dev.arcmem.core.memory.event;
import dev.arcmem.core.memory.budget.*;
import dev.arcmem.core.memory.canon.*;
import dev.arcmem.core.memory.conflict.*;
import dev.arcmem.core.memory.engine.*;
import dev.arcmem.core.memory.maintenance.*;
import dev.arcmem.core.memory.model.*;
import dev.arcmem.core.memory.mutation.*;
import dev.arcmem.core.memory.trust.*;
import dev.arcmem.core.assembly.budget.*;
import dev.arcmem.core.assembly.compaction.*;
import dev.arcmem.core.assembly.compliance.*;
import dev.arcmem.core.assembly.protection.*;
import dev.arcmem.core.assembly.retrieval.*;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.List;

/**
 * Sealed base for all unit lifecycle events.
 * <p>
 * Each concrete subtype is a static nested class; use the static factory methods
 * ({@link #promoted}, {@link #reinforced}, etc.) to construct events rather than
 * the nested constructors directly.
 *
 * <h2>Event types</h2>
 * <ul>
 *   <li>{@link Promoted} — a proposition was promoted to unit status</li>
 *   <li>{@link Reinforced} — an unit's rank was boosted via reinforcement</li>
 *   <li>{@link Archived} — an unit was deactivated</li>
 *   <li>{@link Evicted} — an unit was removed from the budget by eviction</li>
 *   <li>{@link ConflictDetected} — incoming text conflicts with active units</li>
 *   <li>{@link ConflictResolved} — a conflict was resolved</li>
 *   <li>{@link AuthorityChanged} — an unit's authority was promoted or demoted</li>
 *   <li>{@link TierChanged} — an unit's memory tier changed due to rank modification</li>
 *   <li>{@link Superseded} — one unit superseded another (F04)</li>
 * </ul>
 */
public abstract sealed class MemoryUnitLifecycleEvent extends ApplicationEvent
        permits MemoryUnitLifecycleEvent.Promoted,
                MemoryUnitLifecycleEvent.Reinforced,
                MemoryUnitLifecycleEvent.Archived,
                MemoryUnitLifecycleEvent.Evicted,
                MemoryUnitLifecycleEvent.ConflictDetected,
                MemoryUnitLifecycleEvent.ConflictResolved,
                MemoryUnitLifecycleEvent.AuthorityChanged,
                MemoryUnitLifecycleEvent.TierChanged,
                MemoryUnitLifecycleEvent.Superseded,
                MemoryUnitLifecycleEvent.InvariantViolation,
                MemoryUnitLifecycleEvent.PressureThresholdBreached {

    private final String contextId;
    private final Instant occurredAt;

    protected MemoryUnitLifecycleEvent(Object source, String contextId) {
        super(source);
        this.contextId = contextId;
        this.occurredAt = Instant.now();
    }

    public String getContextId() { return contextId; }
    public Instant getOccurredAt() { return occurredAt; }

    public static Promoted promoted(Object source, String contextId,
                                    String propositionId, String unitId, int initialRank) {
        return new Promoted(source, contextId, propositionId, unitId, initialRank);
    }

    public static Reinforced reinforced(Object source, String contextId,
                                        String unitId, int previousRank, int newRank,
                                        int reinforcementCount) {
        return new Reinforced(source, contextId, unitId, previousRank, newRank, reinforcementCount);
    }

    public static Archived archived(Object source, String contextId,
                                    String unitId, ArchiveReason reason) {
        return new Archived(source, contextId, unitId, reason);
    }

    public static Evicted evicted(Object source, String contextId,
                                  String unitId, int previousRank) {
        return new Evicted(source, contextId, unitId, previousRank);
    }

    public static ConflictDetected conflictDetected(Object source, String contextId,
                                                    String incomingText, int conflictCount,
                                                    List<String> conflictingUnitIds) {
        return new ConflictDetected(source, contextId, incomingText, conflictCount, conflictingUnitIds);
    }

    public static ConflictResolved conflictResolved(Object source, String contextId,
                                                    String existingUnitId,
                                                    ConflictResolver.Resolution resolution) {
        return new ConflictResolved(source, contextId, existingUnitId, resolution);
    }

    public static TierChanged tierChanged(Object source, String contextId,
                                           String unitId, MemoryTier previousTier,
                                           MemoryTier newTier) {
        return new TierChanged(source, contextId, unitId, previousTier, newTier);
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
                                                    String unitId, Authority previousAuthority,
                                                    Authority newAuthority,
                                                    AuthorityChangeDirection direction,
                                                    String reason) {
        return new AuthorityChanged(source, contextId, unitId, previousAuthority, newAuthority,
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
            @Nullable String unitId) {
        return new InvariantViolation(source, contextId, ruleId, strength,
                blockedAction, constraintDescription, unitId);
    }

    public static InvariantViolation invariantViolation(
            Object source, String contextId, InvariantViolationData violation) {
        return new InvariantViolation(source, contextId, violation.ruleId(),
                violation.strength(), violation.blockedAction(),
                violation.constraintDescription(), violation.unitId());
    }

    public static PressureThresholdBreached pressureThresholdBreached(Object source, String contextId,
                                                                       PressureScore pressureScore,
                                                                       String thresholdType) {
        return new PressureThresholdBreached(source, contextId, pressureScore, thresholdType);
    }


    /** Published when a proposition is promoted to unit status. */
    public static final class Promoted extends MemoryUnitLifecycleEvent {
        private final String propositionId;
        private final String unitId;
        private final int initialRank;

        private Promoted(Object source, String contextId,
                         String propositionId, String unitId, int initialRank) {
            super(source, contextId);
            this.propositionId = propositionId;
            this.unitId = unitId;
            this.initialRank = initialRank;
        }

        public String getPropositionId() { return propositionId; }
        public String getUnitId() { return unitId; }
        public int getInitialRank() { return initialRank; }
    }

    /** Published when an existing unit's rank is boosted via reinforcement. */
    public static final class Reinforced extends MemoryUnitLifecycleEvent {
        private final String unitId;
        private final int previousRank;
        private final int newRank;
        private final int reinforcementCount;

        private Reinforced(Object source, String contextId,
                           String unitId, int previousRank, int newRank, int reinforcementCount) {
            super(source, contextId);
            this.unitId = unitId;
            this.previousRank = previousRank;
            this.newRank = newRank;
            this.reinforcementCount = reinforcementCount;
        }

        public String getUnitId() { return unitId; }
        public int getPreviousRank() { return previousRank; }
        public int getNewRank() { return newRank; }
        public int getReinforcementCount() { return reinforcementCount; }
    }

    /** Published when an unit is archived (deactivated). */
    public static final class Archived extends MemoryUnitLifecycleEvent {
        private final String unitId;
        private final ArchiveReason reason;

        private Archived(Object source, String contextId, String unitId, ArchiveReason reason) {
            super(source, contextId);
            this.unitId = unitId;
            this.reason = reason;
        }

        public String getUnitId() { return unitId; }
        public ArchiveReason getReason() { return reason; }
    }

    /**
     * Published when an unit is removed from the budget by eviction.
     * Eviction occurs during {@code ArcMemEngine.promote()} when the active count
     * exceeds the configured budget. The lowest-ranked non-pinned unit is evicted.
     * This event fires after the {@link Promoted} event for the incoming unit.
     */
    public static final class Evicted extends MemoryUnitLifecycleEvent {
        private final String unitId;
        private final int previousRank;

        private Evicted(Object source, String contextId, String unitId, int previousRank) {
            super(source, contextId);
            this.unitId = unitId;
            this.previousRank = previousRank;
        }

        public String getUnitId() { return unitId; }
        public int getPreviousRank() { return previousRank; }
    }

    /** Published when incoming text conflicts with one or more active units. */
    public static final class ConflictDetected extends MemoryUnitLifecycleEvent {
        private final String incomingText;
        private final int conflictCount;
        private final List<String> conflictingUnitIds;

        private ConflictDetected(Object source, String contextId,
                                 String incomingText, int conflictCount,
                                 List<String> conflictingUnitIds) {
            super(source, contextId);
            this.incomingText = incomingText;
            this.conflictCount = conflictCount;
            this.conflictingUnitIds = List.copyOf(conflictingUnitIds);
        }

        public String getIncomingText() { return incomingText; }
        public int getConflictCount() { return conflictCount; }
        public List<String> getConflictingUnitIds() { return conflictingUnitIds; }
    }

    /** Published after a conflict between an incoming proposition and an existing unit is resolved. */
    public static final class ConflictResolved extends MemoryUnitLifecycleEvent {
        private final String existingUnitId;
        private final ConflictResolver.Resolution resolution;

        private ConflictResolved(Object source, String contextId,
                                 String existingUnitId, ConflictResolver.Resolution resolution) {
            super(source, contextId);
            this.existingUnitId = existingUnitId;
            this.resolution = resolution;
        }

        public String getExistingUnitId() { return existingUnitId; }
        public ConflictResolver.Resolution getResolution() { return resolution; }
    }

    /**
     * Published when an unit's authority level changes — either promoted (upward)
     * or demoted (downward).
     *
     * <p>All authority transitions, in both directions, publish this event (invariant A3e).
     *
     * <p>For promotions, {@code reason} is typically {@code "reinforcement"} or
     * {@code "trust-evaluation"}. For demotions, {@code reason} is the
     * {@link dev.arcmem.core.memory.canon.DemotionReason#name()} string.
     */
    public static final class AuthorityChanged extends MemoryUnitLifecycleEvent {
        private final String unitId;
        private final Authority previousAuthority;
        private final Authority newAuthority;
        private final AuthorityChangeDirection direction;
        private final String reason;

        private AuthorityChanged(Object source, String contextId,
                                  String unitId, Authority previousAuthority,
                                  Authority newAuthority, AuthorityChangeDirection direction,
                                  String reason) {
            super(source, contextId);
            this.unitId = unitId;
            this.previousAuthority = previousAuthority;
            this.newAuthority = newAuthority;
            this.direction = direction;
            this.reason = reason;
        }

        public String getUnitId() { return unitId; }
        public Authority getPreviousAuthority() { return previousAuthority; }
        public Authority getNewAuthority() { return newAuthority; }
        public AuthorityChangeDirection getDirection() { return direction; }
        public String getReason() { return reason; }
    }

    /**
     * Published when an unit's memory tier changes as a result of a rank-modifying
     * operation (reinforce, decay). Not published on initial promotion (initial assignment
     * is not a transition).
     */
    public static final class TierChanged extends MemoryUnitLifecycleEvent {
        private final String unitId;
        private final MemoryTier previousTier;
        private final MemoryTier newTier;

        private TierChanged(Object source, String contextId,
                            String unitId, MemoryTier previousTier, MemoryTier newTier) {
            super(source, contextId);
            this.unitId = unitId;
            this.previousTier = previousTier;
            this.newTier = newTier;
        }

        public String getUnitId() { return unitId; }
        public MemoryTier getPreviousTier() { return previousTier; }
        public MemoryTier getNewTier() { return newTier; }
    }

    /**
     * Published when one unit supersedes another (e.g., conflict replacement).
     * The predecessor is archived and the successor takes its place.
     */
    public static final class Superseded extends MemoryUnitLifecycleEvent {
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
    public static final class InvariantViolation extends MemoryUnitLifecycleEvent {
        private final String ruleId;
        private final InvariantStrength strength;
        private final ProposedAction blockedAction;
        private final String constraintDescription;
        private final @Nullable String unitId;

        private InvariantViolation(Object source, String contextId,
                                   String ruleId, InvariantStrength strength,
                                   ProposedAction blockedAction,
                                   String constraintDescription,
                                   @Nullable String unitId) {
            super(source, contextId);
            this.ruleId = ruleId;
            this.strength = strength;
            this.blockedAction = blockedAction;
            this.constraintDescription = constraintDescription;
            this.unitId = unitId;
        }

        public String getRuleId() { return ruleId; }
        public InvariantStrength getStrength() { return strength; }
        public ProposedAction getBlockedAction() { return blockedAction; }
        public String getConstraintDescription() { return constraintDescription; }
        public @Nullable String getUnitId() { return unitId; }
    }

    public static final class PressureThresholdBreached extends MemoryUnitLifecycleEvent {
        private final PressureScore pressureScore;
        private final String thresholdType;

        private PressureThresholdBreached(Object source, String contextId,
                                          PressureScore pressureScore, String thresholdType) {
            super(source, contextId);
            this.pressureScore = pressureScore;
            this.thresholdType = thresholdType;
        }

        public PressureScore getPressureScore() { return pressureScore; }
        public String getThresholdType() { return thresholdType; }
    }
}
