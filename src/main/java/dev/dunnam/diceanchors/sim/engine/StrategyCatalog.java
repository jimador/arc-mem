package dev.dunnam.diceanchors.sim.engine;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catalog of drift strategy definitions loaded from a YAML classpath resource.
 * <p>
 * Strategies are indexed by {@link DriftStrategyDefinition#id()} for O(1) lookup.
 * All returned collections are unmodifiable.
 * <p>
 * Use {@link #loadFromClasspath(String)} to load from a YAML file with a {@code strategies}
 * root key. The canonical resource path is {@code "simulations/strategy-catalog.yml"}.
 */
public class StrategyCatalog {

    private final List<DriftStrategyDefinition> strategies;
    private final Map<String, DriftStrategyDefinition> byId;

    private StrategyCatalog(List<DriftStrategyDefinition> strategies) {
        this.strategies = List.copyOf(strategies);
        this.byId = this.strategies.stream()
                                   .collect(Collectors.toUnmodifiableMap(DriftStrategyDefinition::id, Function.identity()));
    }

    /**
     * Loads a strategy catalog from the given classpath resource.
     *
     * @param resourcePath classpath path to a YAML file with a {@code strategies} root key
     *
     * @return parsed catalog
     *
     * @throws IllegalArgumentException if the resource is not found
     * @throws UncheckedIOException     if the YAML cannot be parsed
     */
    public static StrategyCatalog loadFromClasspath(String resourcePath) {
        var mapper = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (InputStream is = StrategyCatalog.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            var wrapper = mapper.readValue(is, CatalogWrapper.class);
            return new StrategyCatalog(wrapper.strategies());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load strategy catalog from " + resourcePath, e);
        }
    }

    public List<DriftStrategyDefinition> all() {
        return strategies;
    }

    public Optional<DriftStrategyDefinition> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<DriftStrategyDefinition> findByTier(StrategyTier tier) {
        return strategies.stream()
                         .filter(s -> s.tier() == tier)
                         .toList();
    }

    public List<DriftStrategyDefinition> findByCategory(String category) {
        return strategies.stream()
                         .filter(s -> s.applicableCategories().contains(category))
                         .toList();
    }

    public List<DriftStrategyDefinition> findByTierAtOrBelow(StrategyTier maxTier) {
        return strategies.stream()
                         .filter(s -> s.tier().isAtOrBelow(maxTier))
                         .toList();
    }

    private record CatalogWrapper(List<DriftStrategyDefinition> strategies) {}
}
