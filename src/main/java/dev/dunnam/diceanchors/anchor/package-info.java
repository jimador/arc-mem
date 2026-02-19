/**
 * Anchor lifecycle management: propositions promoted with rank, authority, and reinforcement tracking.
 * <p>
 * {@link dev.dunnam.diceanchors.anchor.Anchor} represents a promoted proposition.
 * Key fields: {@code rank} (clamped to [100, 900]), {@code authority} (PROVISIONAL → UNRELIABLE → RELIABLE → CANON),
 * {@code reinforcementCount}, {@code pinned} status, and optional {@code trustScore}.
 * Invariants: rank is clamped via {@link dev.dunnam.diceanchors.anchor.Anchor#clampRank(int)},
 * authority only upgrades (never downgrades), CANON is never auto-assigned, and pinned anchors are immune to rank-based eviction.
 * <p>
 * {@link dev.dunnam.diceanchors.anchor.AnchorEngine} is the lifecycle entry point.
 * It orchestrates budget enforcement (max 20 active anchors), rank-based eviction of lowest-ranked non-pinned anchors when over budget,
 * promotion of new anchors, reinforcement via configurable policies, and conflict detection/resolution.
 * <p>
 * Key policies and utilities:
 * <ul>
 *   <li>{@link dev.dunnam.diceanchors.anchor.CompliancePolicy}: determines whether a proposition complies with simulator expectations</li>
 *   <li>{@link dev.dunnam.diceanchors.anchor.ConflictDetector}: identifies contradictory anchors (pluggable: {@link dev.dunnam.diceanchors.anchor.NegationConflictDetector} for negation-based, {@link dev.dunnam.diceanchors.anchor.LlmConflictDetector} for LLM-based)</li>
 *   <li>{@link dev.dunnam.diceanchors.anchor.ConflictResolver}: resolves conflicts by authority hierarchy</li>
 *   <li>{@link dev.dunnam.diceanchors.anchor.ReinforcementPolicy}: defines when anchors gain rank and confidence on reuse</li>
 * </ul>
 * Lifecycle events ({@code event/} package) are published on promotion, reinforcement, conflict, and archival.
 */
package dev.dunnam.diceanchors.anchor;
