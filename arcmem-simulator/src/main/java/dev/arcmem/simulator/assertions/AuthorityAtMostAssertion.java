package dev.arcmem.simulator.assertions;
import dev.arcmem.core.memory.budget.*;
import dev.arcmem.core.memory.canon.*;
import dev.arcmem.core.memory.conflict.*;
import dev.arcmem.core.memory.engine.*;
import dev.arcmem.core.memory.maintenance.*;
import dev.arcmem.core.memory.model.*;
import dev.arcmem.core.memory.mutation.*;
import dev.arcmem.core.memory.trust.*;
import dev.arcmem.core.assembly.budget.*;
import dev.arcmem.core.assembly.compaction.*;
import dev.arcmem.core.assembly.compliance.*;
import dev.arcmem.core.assembly.protection.*;
import dev.arcmem.core.assembly.retrieval.*;

import dev.arcmem.simulator.engine.*;
import dev.arcmem.simulator.scenario.*;

import dev.arcmem.simulator.engine.AssertionResult;
import dev.arcmem.simulator.engine.SimulationAssertion;
import dev.arcmem.simulator.engine.SimulationResult;

import java.util.Map;

/**
 * Asserts no unit exceeds the specified maximum {@link Authority} level.
 */
public class AuthorityAtMostAssertion implements SimulationAssertion {

    private final Authority maxAuthority;

    public AuthorityAtMostAssertion(Map<String, Object> params) {
        var authorityName = (String) params.getOrDefault("maxAuthority", "RELIABLE");
        this.maxAuthority = Authority.valueOf(authorityName);
    }

    @Override
    public AssertionResult evaluate(SimulationResult result) {
        var violations = result.finalUnits().stream()
                               .filter(a -> a.authority().level() > maxAuthority.level())
                               .toList();

        if (violations.isEmpty()) {
            return AssertionResult.pass("authority-at-most",
                                        "All units at or below %s".formatted(maxAuthority));
        }

        var details = new StringBuilder("Units exceeding %s:".formatted(maxAuthority));
        for (var unit : violations) {
            details.append(" [%s=%s]".formatted(unit.id(), unit.authority()));
        }
        return AssertionResult.fail("authority-at-most", details.toString());
    }
}
