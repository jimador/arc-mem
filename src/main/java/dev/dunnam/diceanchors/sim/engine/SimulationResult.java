package dev.dunnam.diceanchors.sim.engine;

import dev.dunnam.diceanchors.anchor.Anchor;

import java.util.List;

/**
 * Aggregate result of a completed simulation run, used as input to assertions.
 */
public record SimulationResult(
        String scenarioId,
        List<Anchor> finalAnchors,
        List<SimulationTurn> turns,
        List<String> groundTruthTexts,
        boolean injectionEnabled
) {}
