package dev.dunnam.diceanchors.sim.engine;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.dunnam.diceanchors.assembly.EnforcementStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScenarioLoader — enforcementStrategy YAML parsing")
class ScenarioLoaderTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private SimulationScenario parse(String yaml) throws Exception {
        return YAML.readValue(yaml, SimulationScenario.class);
    }

    @Nested
    @DisplayName("enforcementStrategy field")
    class EnforcementStrategyField {

        @Test
        @DisplayName("defaults to PROMPT_ONLY when field is absent")
        void defaultsToPromptOnlyWhenAbsent() throws Exception {
            var scenario = parse("""
                    id: test-scenario
                    maxTurns: 5
                    warmUpTurns: 0
                    adversarial: false
                    """);

            assertThat(scenario.enforcementStrategy()).isNull();
            assertThat(scenario.effectiveEnforcementStrategy()).isEqualTo(EnforcementStrategy.PROMPT_ONLY);
        }

        @Test
        @DisplayName("parses LOGIT_BIAS from YAML")
        void parsesLogitBias() throws Exception {
            var scenario = parse("""
                    id: test-scenario
                    maxTurns: 5
                    warmUpTurns: 0
                    adversarial: true
                    enforcementStrategy: LOGIT_BIAS
                    """);

            assertThat(scenario.effectiveEnforcementStrategy()).isEqualTo(EnforcementStrategy.LOGIT_BIAS);
        }

        @Test
        @DisplayName("parses HYBRID from YAML")
        void parsesHybrid() throws Exception {
            var scenario = parse("""
                    id: test-scenario
                    maxTurns: 5
                    warmUpTurns: 0
                    adversarial: true
                    enforcementStrategy: HYBRID
                    """);

            assertThat(scenario.effectiveEnforcementStrategy()).isEqualTo(EnforcementStrategy.HYBRID);
        }

        @Test
        @DisplayName("parses explicit PROMPT_ONLY from YAML")
        void parsesExplicitPromptOnly() throws Exception {
            var scenario = parse("""
                    id: test-scenario
                    maxTurns: 5
                    warmUpTurns: 0
                    adversarial: false
                    enforcementStrategy: PROMPT_ONLY
                    """);

            assertThat(scenario.effectiveEnforcementStrategy()).isEqualTo(EnforcementStrategy.PROMPT_ONLY);
        }
    }

    @Nested
    @DisplayName("complianceRate in ScoringResult")
    class ComplianceRateMetric {

        @Test
        @DisplayName("ScoringService computes complianceRate from evaluated turns")
        void complianceRateFromEvaluatedTurns() {
            var service = new ScoringService();
            var snapshots = java.util.List.of(
                    snapshot(1, java.util.List.of(EvalVerdict.confirmed("f1", "ok"))),
                    snapshot(2, java.util.List.of(EvalVerdict.contradicted("f1", EvalVerdict.Severity.MAJOR, "drift"))),
                    snapshot(3, java.util.List.of(EvalVerdict.confirmed("f1", "back on track")))
            );
            var groundTruth = java.util.List.of(new SimulationScenario.GroundTruth("f1", "The king is alive"));
            var result = service.score(snapshots, groundTruth);

            // 3 evaluated turns, 1 contradiction → compliance rate ≈ 66.7%
            assertThat(result.complianceRate()).isGreaterThan(0.0).isLessThan(100.0);
        }

        @Test
        @DisplayName("complianceRate is 0.0 when no evaluated turns")
        void complianceRateZeroForNoEvaluatedTurns() {
            var service = new ScoringService();
            var snapshots = java.util.List.of(
                    snapshot(1, java.util.List.of()) // no verdicts = not evaluated
            );
            var groundTruth = java.util.List.of(new SimulationScenario.GroundTruth("f1", "fact"));
            var result = service.score(snapshots, groundTruth);

            assertThat(result.complianceRate()).isEqualTo(0.0);
        }
    }

    private static SimulationRunRecord.TurnSnapshot snapshot(int turn, java.util.List<EvalVerdict> verdicts) {
        return new SimulationRunRecord.TurnSnapshot(
                turn, TurnType.ATTACK, java.util.List.of(),
                "player", "dm",
                java.util.List.of(), null, verdicts, true, null);
    }
}
