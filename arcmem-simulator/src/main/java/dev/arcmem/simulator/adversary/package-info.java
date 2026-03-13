/**
 * Adaptive adversary strategy and attack planning for adversarial simulation scenarios.
 * <p>
 * {@link dev.arcmem.simulator.adversary.TieredEscalationStrategy} is the primary
 * attack planner. It selects attacks by targeting the weakest (lowest-rank) active units
 * and escalates strategy tier when prior attacks against a specific target fail.
 * All decisions are history-driven via {@link dev.arcmem.simulator.adversary.AttackHistory};
 * the strategy carries no mutable state.
 * Supports multi-turn attack sequences ({@link dev.arcmem.simulator.adversary.AttackSequence})
 * that chain SETUP → BUILD → PAYOFF phases.
 * <p>
 * {@link dev.arcmem.simulator.adversary.AdaptiveAttackPrompter} converts an
 * {@link dev.arcmem.simulator.adversary.AttackPlan} into in-character player dialogue
 * via a single LLM call, embedding the planned attack in natural conversation.
 * <p>
 * Data structures:
 * <ul>
 *   <li>{@link dev.arcmem.simulator.adversary.AttackPlan}: immutable specification of target facts, strategies, and tier</li>
 *   <li>{@link dev.arcmem.simulator.adversary.AttackOutcome}: record of attack result (target, success/failure, verdicts)</li>
 *   <li>{@link dev.arcmem.simulator.adversary.AttackSequence}: multi-turn chain phase tracker</li>
 *   <li>StrategyCatalog (in {@code sim.engine}): metadata for all attack strategies loaded from YAML</li>
 * </ul>
 */
package dev.arcmem.simulator.adversary;
