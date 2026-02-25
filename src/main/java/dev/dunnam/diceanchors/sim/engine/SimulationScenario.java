package dev.dunnam.diceanchors.sim.engine;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

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
        ModelConfig generatorModel,
        ModelConfig evaluatorModel,
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
        List<String> highlights,
        @Nullable String adversaryMode,
        @Nullable AdversaryConfig adversaryConfig,
        @Nullable List<InvariantRuleDef> invariants
) {
    /**
     * Configuration for the player character persona during simulation.
     *
     * @param name        human-readable character name
     * @param description character background and context
     * @param playStyle   behavioral archetype (e.g., "cunning", "honorable")
     * @param goals       character objectives that guide player turn prompts
     */
    public record PersonaConfig(String name, String description, String playStyle, List<String> goals) {
        public List<String> effectiveGoals() {
            return goals != null ? goals : List.of();
        }
    }

    /**
     * Configuration for the adaptive adversary engine.
     *
     * @param aggressiveness    0.0–1.0, controls how many anchors to target per turn
     * @param maxEscalationTier 1–4, caps the strategy tier (maps to StrategyTier ordinal + 1)
     * @param preferredStrategies optional list of strategy IDs from the catalog to prefer
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdversaryConfig(
            double aggressiveness,
            int maxEscalationTier,
            List<String> preferredStrategies,
            @Nullable Integer attackCooldown
    ) {
        public AdversaryConfig {
            aggressiveness = Math.max(0.0, Math.min(1.0, aggressiveness));
            maxEscalationTier = Math.max(1, Math.min(4, maxEscalationTier));
            preferredStrategies = preferredStrategies != null ? List.copyOf(preferredStrategies) : List.of();
        }

        /** Min turns that must pass between attacks. Defaults to 2 if not configured. */
        public int effectiveAttackCooldown() {
            return attackCooldown != null ? Math.max(0, attackCooldown) : 2;
        }

        public static AdversaryConfig defaults() {
            return new AdversaryConfig(0.5, 3, List.of(), null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelConfig(String provider, String model, Double temperature) {}

    /**
     * Factual statement that the adversary will attempt to corrupt or drift in ATTACK/DISPLACEMENT/DRIFT/RECALL_PROBE turns.
     * Evaluated against DM responses to measure drift.
     */
    public record GroundTruth(String id, @JsonAlias("fact") String text) {}

    public record SeedAnchor(String text, String authority, int rank) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TrustConfig(String profile, Map<String, Double> weightOverrides) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompactionConfig(
            boolean enabled,
            List<Integer> forceAtTurns,
            int tokenThreshold,
            int messageThreshold,
            @Nullable Double minMatchRatio,
            @Nullable Integer maxRetries,
            @Nullable Long retryBackoffMillis,
            @Nullable Boolean eventsEnabled
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DormancyConfig(double decayRate, double revivalThreshold, int dormancyTurns) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionConfig(String name, int startTurn, int endTurn) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AssertionConfig(String type, Map<String, Object> params) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InvariantRuleDef(
            String id,
            String type,
            @Nullable String strength,
            @Nullable String contextId,
            @Nullable String anchorTextPattern,
            @Nullable String minimumAuthority,
            @Nullable Integer minimumCount
    ) {}

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
     * Returns the adversary mode, defaulting to "scripted" if not set.
     */
    public String effectiveAdversaryMode() {
        return adversaryMode != null ? adversaryMode : "scripted";
    }

    /**
     * Returns the adversary configuration, defaulting to {@link AdversaryConfig#defaults()} if not set.
     */
    public AdversaryConfig effectiveAdversaryConfig() {
        return adversaryConfig != null ? adversaryConfig : AdversaryConfig.defaults();
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
