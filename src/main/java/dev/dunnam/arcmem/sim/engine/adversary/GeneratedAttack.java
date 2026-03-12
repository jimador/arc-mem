package dev.dunnam.diceanchors.sim.engine.adversary;

/**
 * Result of one LLM-driven adaptive attack turn: the strategies the LLM chose to apply
 * and the generated in-character player message.
 */
public record GeneratedAttack(AttackPlan plan, String message) {}
