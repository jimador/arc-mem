package dev.dunnam.diceanchors.sim.report;

import java.util.Map;

/**
 * Strategy effectiveness breakdown for a resilience report.
 *
 * @param strategies strategy name to (condition name to mean effectiveness)
 */
public record StrategySection(Map<String, Map<String, Double>> strategies) {
}
