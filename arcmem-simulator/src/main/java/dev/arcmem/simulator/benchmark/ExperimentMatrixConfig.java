package dev.arcmem.simulator.benchmark;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public record ExperimentMatrixConfig(
        String name,
        List<String> conditions,
        List<String> scenarios,
        int repetitions,
        @Nullable String model,
        String outputDir
) {
    public ExperimentMatrixConfig {
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("At least one condition required");
        }
        if (scenarios == null || scenarios.isEmpty()) {
            throw new IllegalArgumentException("At least one scenario required");
        }
        if (repetitions < 2) {
            throw new IllegalArgumentException("At least 2 repetitions required, got " + repetitions);
        }
        if (outputDir == null || outputDir.isBlank()) {
            outputDir = "experiment-output";
        }
        conditions = List.copyOf(conditions);
        scenarios = List.copyOf(scenarios);
    }

    public List<AblationCondition> resolveConditions() {
        return conditions.stream()
                .map(ExperimentMatrixConfig::resolveCondition)
                .toList();
    }

    private static AblationCondition resolveCondition(String name) {
        return switch (name) {
            case "FULL_AWMU" -> AblationCondition.FULL_AWMU;
            case "NO_AWMU" -> AblationCondition.NO_AWMU;
            case "FLAT_AUTHORITY" -> AblationCondition.FLAT_AUTHORITY;
            case "NO_RANK_DIFFERENTIATION" -> AblationCondition.NO_RANK_DIFFERENTIATION;
            case "NO_TRUST" -> AblationCondition.NO_TRUST;
            case "NO_COMPLIANCE" -> AblationCondition.NO_COMPLIANCE;
            case "NO_LIFECYCLE" -> AblationCondition.NO_LIFECYCLE;
            default -> throw new IllegalArgumentException("Unknown condition: " + name);
        };
    }
}
