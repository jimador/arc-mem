package dev.dunnam.diceanchors.sim.assertions;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.sim.engine.AssertionResult;
import dev.dunnam.diceanchors.sim.engine.SimulationAssertion;
import dev.dunnam.diceanchors.sim.engine.SimulationResult;

import java.util.List;
import java.util.Map;

/**
 * Asserts that the final anchor context contains all required fact texts (case-insensitive substring match).
 * Used to verify compaction does not lose critical information.
 */
public class CompactionIntegrityAssertion implements SimulationAssertion {

    private final List<String> requiredFacts;

    @SuppressWarnings("unchecked")
    public CompactionIntegrityAssertion(Map<String, Object> params) {
        var raw = params.get("requiredFacts");
        this.requiredFacts = raw instanceof List<?> list
                ? ((List<String>) list).stream().map(String::toLowerCase).toList()
                : List.of();
    }

    @Override
    public AssertionResult evaluate(SimulationResult result) {
        var anchorTexts = result.finalAnchors().stream()
                                .map(Anchor::text)
                                .map(String::toLowerCase)
                                .toList();

        var missing = requiredFacts.stream()
                                   .filter(fact -> anchorTexts.stream().noneMatch(t -> t.contains(fact)))
                                   .toList();

        if (missing.isEmpty()) {
            return AssertionResult.pass("compaction-integrity",
                                        "All %d required facts preserved".formatted(requiredFacts.size()));
        }

        return AssertionResult.fail("compaction-integrity",
                                    "Missing facts after compaction: %s".formatted(missing));
    }
}
