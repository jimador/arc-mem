package dev.dunnam.diceanchors.sim.assertions;

import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.sim.engine.AssertionResult;
import dev.dunnam.diceanchors.sim.engine.SimulationAssertion;
import dev.dunnam.diceanchors.sim.engine.SimulationResult;

import java.util.Map;

/**
 * Asserts no anchor exceeds the specified maximum {@link Authority} level.
 */
public class AuthorityAtMostAssertion implements SimulationAssertion {

    private final Authority maxAuthority;

    public AuthorityAtMostAssertion(Map<String, Object> params) {
        var authorityName = (String) params.getOrDefault("maxAuthority", "RELIABLE");
        this.maxAuthority = Authority.valueOf(authorityName);
    }

    @Override
    public AssertionResult evaluate(SimulationResult result) {
        var violations = result.finalAnchors().stream()
                               .filter(a -> a.authority().level() > maxAuthority.level())
                               .toList();

        if (violations.isEmpty()) {
            return AssertionResult.pass("authority-at-most",
                                        "All anchors at or below %s".formatted(maxAuthority));
        }

        var details = new StringBuilder("Anchors exceeding %s:".formatted(maxAuthority));
        for (var anchor : violations) {
            details.append(" [%s=%s]".formatted(anchor.id(), anchor.authority()));
        }
        return AssertionResult.fail("authority-at-most", details.toString());
    }
}
