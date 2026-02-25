package dev.dunnam.diceanchors.sim.assertions;

import dev.dunnam.diceanchors.sim.engine.SimulationAssertion;
import dev.dunnam.diceanchors.sim.engine.SimulationScenario;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Registry that resolves assertion type strings from YAML scenario config
 * into concrete {@link SimulationAssertion} instances.
 */
@Component
public class AssertionRegistry {

    private static final Map<String, Function<Map<String, Object>, SimulationAssertion>> FACTORIES = Map.ofEntries(
            Map.entry("anchor-count", AnchorCountAssertion::new),
            Map.entry("rank-distribution", RankDistributionAssertion::new),
            Map.entry("trust-score-range", TrustScoreRangeAssertion::new),
            Map.entry("promotion-zone", PromotionZoneAssertion::new),
            Map.entry("authority-at-most", AuthorityAtMostAssertion::new),
            Map.entry("kg-context-contains", KgContextContainsAssertion::new),
            Map.entry("kg-context-empty", p -> new KgContextEmptyAssertion()),
            Map.entry("no-canon-auto-assigned", p -> new NoCanonAutoAssignedAssertion()),
            Map.entry("compaction-integrity", CompactionIntegrityAssertion::new)
    );

    /**
     * Resolve a single assertion by type name and parameter map.
     *
     * @param type   the assertion type key (e.g. "anchor-count")
     * @param params parameter map from YAML config; may be null
     *
     * @throws IllegalArgumentException if the type is unknown
     */
    public SimulationAssertion resolve(String type, Map<String, Object> params) {
        var factory = FACTORIES.get(type);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown assertion type: " + type);
        }
        return factory.apply(params != null ? params : Map.of());
    }

    /**
     * Resolve all assertions from a scenario's assertion config list.
     *
     * @param configs assertion configs from the scenario YAML
     * @return instantiated assertions; empty list if configs is null or empty
     */
    public List<SimulationAssertion> resolveAll(List<SimulationScenario.AssertionConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return List.of();
        }
        return configs.stream()
                      .map(c -> resolve(c.type(), c.params()))
                      .toList();
    }
}
