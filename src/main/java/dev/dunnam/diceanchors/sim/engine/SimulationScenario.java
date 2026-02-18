package dev.dunnam.diceanchors.sim.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * YAML-loaded scenario definition for adversarial or baseline drift testing.
 * <p>
 * Invariant: scriptedTurns() returns only PLAYER-role turns from the full turns list.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SimulationScenario(
        String id,
        PersonaConfig persona,
        String model,
        @JsonProperty("temperature") Double temperature,
        int maxTurns,
        int warmUpTurns,
        boolean adversarial,
        String setting,
        List<GroundTruth> groundTruth,
        List<SeedAnchor> seedAnchors,
        List<ScriptedTurn> turns,
        String generatorModel,
        String evaluatorModel,
        TrustConfig trustConfig,
        CompactionConfig compactionConfig,
        List<AssertionConfig> assertions,
        DormancyConfig dormancyConfig,
        List<SessionConfig> sessions,
        String category,
        @JsonProperty("extractionEnabled") Boolean extractionEnabled,
        String title,
        String objective,
        String testFocus,
        List<String> highlights
) {
    public record PersonaConfig(String name, String description, String playStyle) {}

    public record GroundTruth(String id, String text) {}

    public record SeedAnchor(String text, String authority, int rank) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TrustConfig(String profile, Map<String, Double> weightOverrides) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompactionConfig(boolean enabled, List<Integer> forceAtTurns, int tokenThreshold, int messageThreshold) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DormancyConfig(double decayRate, double revivalThreshold, int dormancyTurns) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionConfig(String name, int startTurn, int endTurn) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AssertionConfig(String type, Map<String, Object> params) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScriptedTurn(
            int turn,
            String role,
            String type,
            String strategy,
            String prompt,
            String targetFact
    ) {}

    /**
     * Returns the effective temperature, defaulting to 0.7 if not set in the scenario.
     */
    public double effectiveTemperature() {
        return temperature != null ? temperature : 0.7;
    }

    /**
     * Returns whether DICE extraction should run on DM responses during simulation turns.
     * Defaults to true when not specified in the scenario YAML.
     */
    public boolean isExtractionEnabled() {
        return extractionEnabled == null || extractionEnabled;
    }

    /**
     * Human-friendly scenario title for UI display.
     */
    public String displayTitle() {
        if (title != null && !title.isBlank()) {
            return title;
        }
        var raw = id != null ? id : "Unnamed Scenario";
        return toTitleCase(raw.replace('-', ' '));
    }

    /**
     * Short sentence describing what this scenario is validating.
     */
    public String displayObjective() {
        if (objective != null && !objective.isBlank()) {
            return objective;
        }
        if (adversarial) {
            return "Tests anchor resilience under adversarial contradiction pressure.";
        }
        return "Tests anchor stability and factual consistency under normal play.";
    }

    /**
     * Focus area for operators running the scenario.
     */
    public String displayTestFocus() {
        if (testFocus != null && !testFocus.isBlank()) {
            return testFocus;
        }
        var focus = new ArrayList<String>();
        if (category != null && !category.isBlank()) {
            focus.add("category: " + category);
        }
        focus.add(adversarial ? "adversarial turn handling" : "baseline memory behavior");
        focus.add(isExtractionEnabled() ? "extraction enabled" : "extraction disabled");
        if (compactionConfig != null && compactionConfig.enabled()) {
            focus.add("compaction enabled");
        }
        if (dormancyConfig != null && dormancyConfig.dormancyTurns() > 0) {
            focus.add("dormancy lifecycle");
        }
        return String.join(" | ", focus);
    }

    /**
     * Interesting scenario details shown in the simulator briefing panel.
     */
    public List<String> displayHighlights() {
        if (highlights != null && !highlights.isEmpty()) {
            return highlights.stream().filter(item -> item != null && !item.isBlank()).toList();
        }
        var generated = new ArrayList<String>();
        generated.add("Turns: %d total (%d warm-up)".formatted(maxTurns, warmUpTurns));
        generated.add("Ground truth facts: %d".formatted(groundTruth != null ? groundTruth.size() : 0));
        generated.add("Seed anchors: %d".formatted(seedAnchors != null ? seedAnchors.size() : 0));
        generated.add("Scripted player turns: %d".formatted(scriptedTurns().size()));
        if (compactionConfig != null && compactionConfig.enabled()) {
            generated.add("Compaction thresholds: %d tokens / %d messages".formatted(
                    compactionConfig.tokenThreshold(),
                    compactionConfig.messageThreshold()));
        }
        if (dormancyConfig != null && dormancyConfig.dormancyTurns() > 0) {
            generated.add("Dormancy: decay %.2f after %d dormant turns".formatted(
                    dormancyConfig.decayRate(),
                    dormancyConfig.dormancyTurns()));
        }
        return generated;
    }

    /**
     * Returns only PLAYER-role scripted turns, in turn order.
     * DM-role entries (narrative descriptions, etc.) are excluded.
     */
    public List<ScriptedTurn> scriptedTurns() {
        return turns != null ? turns.stream()
                                    .filter(t -> "PLAYER".equals(t.role()))
                                    .toList() : List.of();
    }

    private static String toTitleCase(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        var words = text.trim().split("\\s+");
        var sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            var word = words[i];
            sb.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
            if (word.length() > 1) {
                sb.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }
}
