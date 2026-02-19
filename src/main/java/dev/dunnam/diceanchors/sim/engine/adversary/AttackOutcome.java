package dev.dunnam.diceanchors.sim.engine.adversary;

/**
 * Immutable record of what happened on an adaptive adversary turn.
 * Stored in {@link AttackHistory} to drive tier escalation decisions.
 *
 * @param turn             1-based turn number
 * @param plan             the attack plan executed this turn
 * @param verdictSeverity  "CONTRADICTED" if the attack worked; "NONE" if it had no effect
 */
public record AttackOutcome(
        int turn,
        AttackPlan plan,
        String verdictSeverity
) {
    public AttackOutcome {
        if (plan == null) throw new IllegalArgumentException("plan must not be null");
        verdictSeverity = verdictSeverity != null ? verdictSeverity : "NONE";
    }

    /** Returns true if the attack had any effect (verdictSeverity != "NONE"). */
    public boolean succeeded() {
        return !"NONE".equals(verdictSeverity);
    }
}
