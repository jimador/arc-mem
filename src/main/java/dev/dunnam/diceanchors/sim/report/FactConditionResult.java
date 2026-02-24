package dev.dunnam.diceanchors.sim.report;

import java.util.OptionalInt;

/**
 * Survival result for a single fact under a single condition.
 *
 * @param survived       number of runs where this fact was not contradicted
 * @param total          total runs examined
 * @param firstDriftTurn earliest turn where contradiction occurred; empty if never
 */
public record FactConditionResult(int survived, int total, OptionalInt firstDriftTurn) {
}
