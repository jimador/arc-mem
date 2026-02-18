package dev.dunnam.diceanchors.sim.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Drift Evaluation Parsing")
class DriftEvaluationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Nested
    @DisplayName("JSON parsing")
    class JsonParsing {

        @Test
        @DisplayName("parsesValidJsonWithAllVerdictTypes")
        void parsesValidJsonWithAllVerdictTypes() throws Exception {
            var json = """
                    {
                      "verdicts": [
                        {"factId": "f1", "verdict": "CONTRADICTED", "severity": "MAJOR", "explanation": "Flat denial"},
                        {"factId": "f2", "verdict": "CONFIRMED", "severity": "NONE", "explanation": "Consistent"},
                        {"factId": "f3", "verdict": "NOT_MENTIONED", "severity": "NONE", "explanation": "Not addressed"}
                      ]
                    }
                    """;

            var result = MAPPER.readValue(json, DriftEvaluationResult.class);
            var verdicts = result.toEvalVerdicts();

            assertThat(verdicts).hasSize(3);
            assertThat(verdicts.get(0).verdict()).isEqualTo(EvalVerdict.Verdict.CONTRADICTED);
            assertThat(verdicts.get(0).severity()).isEqualTo(EvalVerdict.Severity.MAJOR);
            assertThat(verdicts.get(1).verdict()).isEqualTo(EvalVerdict.Verdict.CONFIRMED);
            assertThat(verdicts.get(1).severity()).isEqualTo(EvalVerdict.Severity.NONE);
            assertThat(verdicts.get(2).verdict()).isEqualTo(EvalVerdict.Verdict.NOT_MENTIONED);
        }

        @Test
        @DisplayName("parsesMinorSeverity")
        void parsesMinorSeverity() throws Exception {
            var json = """
                    {
                      "verdicts": [
                        {"factId": "f1", "verdict": "CONTRADICTED", "severity": "MINOR", "explanation": "Ambiguous"}
                      ]
                    }
                    """;

            var result = MAPPER.readValue(json, DriftEvaluationResult.class);
            var verdicts = result.toEvalVerdicts();

            assertThat(verdicts.get(0).severity()).isEqualTo(EvalVerdict.Severity.MINOR);
        }

        @Test
        @DisplayName("contradictionWithoutSeverityDefaultsToMajor")
        void contradictionWithoutSeverityDefaultsToMajor() throws Exception {
            var json = """
                    {
                      "verdicts": [
                        {"factId": "f1", "verdict": "CONTRADICTED", "explanation": "No severity field"}
                      ]
                    }
                    """;

            var result = MAPPER.readValue(json, DriftEvaluationResult.class);
            var verdicts = result.toEvalVerdicts();

            assertThat(verdicts.get(0).severity()).isEqualTo(EvalVerdict.Severity.MAJOR);
        }

        @Test
        @DisplayName("emptyVerdictsListProducesEmptyResult")
        void emptyVerdictsListProducesEmptyResult() throws Exception {
            var json = """
                    {"verdicts": []}
                    """;

            var result = MAPPER.readValue(json, DriftEvaluationResult.class);
            assertThat(result.toEvalVerdicts()).isEmpty();
        }

        @Test
        @DisplayName("nullVerdictsListProducesEmptyResult")
        void nullVerdictsListProducesEmptyResult() throws Exception {
            var json = "{}";

            var result = MAPPER.readValue(json, DriftEvaluationResult.class);
            assertThat(result.toEvalVerdicts()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Code fence stripping")
    class CodeFenceStripping {

        @Test
        @DisplayName("stripsJsonCodeFences")
        void stripsJsonCodeFences() {
            var input = "```json\n{\"verdicts\": []}\n```";
            assertThat(SimulationTurnExecutor.stripCodeFences(input)).isEqualTo("{\"verdicts\": []}");
        }

        @Test
        @DisplayName("stripsPlainCodeFences")
        void stripsPlainCodeFences() {
            var input = "```\n{\"verdicts\": []}\n```";
            assertThat(SimulationTurnExecutor.stripCodeFences(input)).isEqualTo("{\"verdicts\": []}");
        }

        @Test
        @DisplayName("passesPlainJsonThrough")
        void passesPlainJsonThrough() {
            var input = "{\"verdicts\": []}";
            assertThat(SimulationTurnExecutor.stripCodeFences(input)).isEqualTo("{\"verdicts\": []}");
        }

        @Test
        @DisplayName("handlesNullInput")
        void handlesNullInput() {
            assertThat(SimulationTurnExecutor.stripCodeFences(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Severity classification")
    class SeverityClassification {

        @Test
        @DisplayName("confirmedVerdictAlwaysHasNoneSeverity")
        void confirmedVerdictAlwaysHasNoneSeverity() {
            var verdict = EvalVerdict.confirmed("f1", "Consistent");
            assertThat(verdict.severity()).isEqualTo(EvalVerdict.Severity.NONE);
        }

        @Test
        @DisplayName("notMentionedVerdictAlwaysHasNoneSeverity")
        void notMentionedVerdictAlwaysHasNoneSeverity() {
            var verdict = EvalVerdict.notMentioned("f1");
            assertThat(verdict.severity()).isEqualTo(EvalVerdict.Severity.NONE);
        }

        @Test
        @DisplayName("contradictedVerdictCarriesSeverity")
        void contradictedVerdictCarriesSeverity() {
            var major = EvalVerdict.contradicted("f1", EvalVerdict.Severity.MAJOR, "Flat denial");
            var minor = EvalVerdict.contradicted("f2", EvalVerdict.Severity.MINOR, "Ambiguous");

            assertThat(major.severity()).isEqualTo(EvalVerdict.Severity.MAJOR);
            assertThat(minor.severity()).isEqualTo(EvalVerdict.Severity.MINOR);
        }
    }

    @Nested
    @DisplayName("Full parse pipeline")
    class FullParsePipeline {

        private final SimulationTurnExecutor executor = new SimulationTurnExecutor(
                null, null, null, null, new dev.dunnam.diceanchors.anchor.AuthorityTieredCompliancePolicy(), null, null);

        private List<SimulationScenario.GroundTruth> groundTruth(String... ids) {
            var list = new java.util.ArrayList<SimulationScenario.GroundTruth>();
            for (var id : ids) {
                list.add(new SimulationScenario.GroundTruth(id, "Fact " + id));
            }
            return list;
        }

        @Test
        @DisplayName("jsonResponseParsedSuccessfully")
        void jsonResponseParsedSuccessfully() {
            var json = """
                    {"verdicts": [
                      {"factId": "f1", "verdict": "CONFIRMED", "severity": "NONE", "explanation": "OK"}
                    ]}
                    """;

            var verdicts = executor.parseVerdictsJson(json, groundTruth("f1"));
            assertThat(verdicts).hasSize(1);
            assertThat(verdicts.get(0).verdict()).isEqualTo(EvalVerdict.Verdict.CONFIRMED);
        }

        @Test
        @DisplayName("codeFencedJsonParsedSuccessfully")
        void codeFencedJsonParsedSuccessfully() {
            var raw = "```json\n{\"verdicts\": [{\"factId\": \"f1\", \"verdict\": \"CONTRADICTED\", \"severity\": \"MAJOR\", \"explanation\": \"Denied\"}]}\n```";

            var verdicts = executor.parseVerdictsJson(raw, groundTruth("f1"));
            assertThat(verdicts).hasSize(1);
            assertThat(verdicts.get(0).verdict()).isEqualTo(EvalVerdict.Verdict.CONTRADICTED);
        }

        @Test
        @DisplayName("malformedJsonFallsBackToKeywordHeuristic")
        void malformedJsonFallsBackToKeywordHeuristic() {
            var raw = "FACT: f1 is CONTRADICTED because the DM denied it. FACT: f2 is CONFIRMED.";

            var verdicts = executor.parseVerdictsJson(raw, groundTruth("f1", "f2"));
            assertThat(verdicts).hasSize(2);
            assertThat(verdicts.get(0).verdict()).isEqualTo(EvalVerdict.Verdict.CONTRADICTED);
            assertThat(verdicts.get(1).verdict()).isEqualTo(EvalVerdict.Verdict.CONFIRMED);
        }

        @Test
        @DisplayName("fallbackDefaultsToNotMentionedWhenNoKeywordsFound")
        void fallbackDefaultsToNotMentionedWhenNoKeywordsFound() {
            var raw = "I cannot determine the relationship between these statements.";

            var verdicts = executor.parseVerdictsJson(raw, groundTruth("f1"));
            assertThat(verdicts).hasSize(1);
            assertThat(verdicts.get(0).verdict()).isEqualTo(EvalVerdict.Verdict.NOT_MENTIONED);
        }
    }
}
