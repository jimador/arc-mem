package dev.dunnam.diceanchors.sim.assertions;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.sim.engine.AssertionResult;
import dev.dunnam.diceanchors.sim.engine.SimulationAssertion;
import dev.dunnam.diceanchors.sim.engine.SimulationResult;

import java.util.List;
import java.util.Map;

/**
 * Asserts that the final anchor texts contain all specified patterns (case-insensitive substring match).
 */
public class KgContextContainsAssertion implements SimulationAssertion {

    private final List<String> patterns;

    @SuppressWarnings("unchecked")
    public KgContextContainsAssertion(Map<String, Object> params) {
        var raw = params.get("patterns");
        this.patterns = raw instanceof List<?> list
                ? ((List<String>) list).stream().map(String::toLowerCase).toList()
                : List.of();
    }

    @Override
    public AssertionResult evaluate(SimulationResult result) {
        var anchorTexts = result.finalAnchors().stream()
                                .map(Anchor::text)
                                .map(String::toLowerCase)
                                .toList();

        var missing = patterns.stream()
                              .filter(pattern -> anchorTexts.stream().noneMatch(t -> t.contains(pattern)))
                              .toList();

        if (missing.isEmpty()) {
            return AssertionResult.pass("kg-context-contains",
                                        "All %d patterns found in anchor context".formatted(patterns.size()));
        }

        return AssertionResult.fail("kg-context-contains",
                                    "Missing patterns: %s".formatted(missing));
    }
}
