package dev.dunnam.diceanchors.sim.engine;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.Authority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScoringService")
class ScoringServiceTest {

    private final ScoringService service = new ScoringService();

    private static SimulationRunRecord.TurnSnapshot snapshot(int turnNumber,
                                                              AttackStrategy strategy,
                                                              List<EvalVerdict> verdicts) {
        return new SimulationRunRecord.TurnSnapshot(
                turnNumber, TurnType.ATTACK, strategy,
                "player message", "dm response",
                List.of(), null, verdicts, true, null
        );
    }

    private static SimulationScenario.GroundTruth fact(String id, String text) {
        return new SimulationScenario.GroundTruth(id, text);
    }

    private static Anchor anchor(String id, String text) {
        return Anchor.withoutTrust(id, text, 500, Authority.RELIABLE, false, 0.9, 0);
    }

    @Nested
    @DisplayName("score")
    class Score {

        @Test
        @DisplayName("perfect run yields 100% survival and absorption with zero contradictions")
        void perfectRunZeroContradictions() {
            var snapshots = List.of(
                    snapshot(1, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.confirmed("f1", "confirmed")
                    )),
                    snapshot(2, AttackStrategy.CONFIDENT_ASSERTION, List.of(
                            EvalVerdict.confirmed("f1", "still confirmed")
                    ))
            );
            var groundTruth = List.of(fact("f1", "The king is alive"));
            var result = service.score(snapshots, groundTruth, List.of());
            assertThat(result.factSurvivalRate()).isEqualTo(100.0);
            assertThat(result.contradictionCount()).isZero();
            assertThat(result.majorContradictionCount()).isZero();
            assertThat(result.driftAbsorptionRate()).isEqualTo(100.0);
            assertThat(result.meanTurnsToFirstDrift()).isNaN();
        }

        @Test
        @DisplayName("partial drift counts contradictions and survival correctly")
        void partialDriftCorrectCounts() {
            var snapshots = List.of(
                    snapshot(1, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.confirmed("f1", "ok"),
                            EvalVerdict.confirmed("f2", "ok")
                    )),
                    snapshot(2, AttackStrategy.CONFIDENT_ASSERTION, List.of(
                            EvalVerdict.contradicted("f1", EvalVerdict.Severity.MAJOR, "drift"),
                            EvalVerdict.confirmed("f2", "ok")
                    )),
                    snapshot(3, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.contradicted("f1", EvalVerdict.Severity.MINOR, "more drift"),
                            EvalVerdict.contradicted("f2", EvalVerdict.Severity.MAJOR, "also drifted")
                    ))
            );
            var groundTruth = List.of(
                    fact("f1", "The king is alive"),
                    fact("f2", "The queen is wise")
            );
            var result = service.score(snapshots, groundTruth, List.of());
            assertThat(result.factSurvivalRate()).isEqualTo(0.0);
            assertThat(result.contradictionCount()).isEqualTo(3);
            assertThat(result.majorContradictionCount()).isEqualTo(2);
            // 1 clean turn out of 3 evaluated
            assertThat(result.driftAbsorptionRate()).isCloseTo(33.33, org.assertj.core.data.Offset.offset(0.1));
        }

        @Test
        @DisplayName("strategy effectiveness groups contradiction rates by strategy")
        void strategyEffectivenessCalculation() {
            var snapshots = List.of(
                    snapshot(1, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.confirmed("f1", "ok")
                    )),
                    snapshot(2, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.contradicted("f1", EvalVerdict.Severity.MINOR, "drift")
                    )),
                    snapshot(3, AttackStrategy.AUTHORITY_HIJACK, List.of(
                            EvalVerdict.contradicted("f1", EvalVerdict.Severity.MAJOR, "hijacked")
                    ))
            );
            var groundTruth = List.of(fact("f1", "The king is alive"));
            var result = service.score(snapshots, groundTruth, List.of());
            // SUBTLE_REFRAME: 1 contradiction / 2 turns = 0.5
            assertThat(result.strategyEffectiveness().get("SUBTLE_REFRAME")).isEqualTo(0.5);
            // AUTHORITY_HIJACK: 1 contradiction / 1 turn = 1.0
            assertThat(result.strategyEffectiveness().get("AUTHORITY_HIJACK")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("meanTurnsToFirstDrift averages earliest contradiction per fact")
        void meanTurnsToFirstDriftCalculation() {
            var snapshots = List.of(
                    snapshot(1, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.confirmed("f1", "ok"),
                            EvalVerdict.confirmed("f2", "ok")
                    )),
                    snapshot(2, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.contradicted("f1", EvalVerdict.Severity.MINOR, "drift")
                    )),
                    snapshot(4, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.contradicted("f2", EvalVerdict.Severity.MINOR, "drift")
                    )),
                    snapshot(5, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.contradicted("f1", EvalVerdict.Severity.MAJOR, "again")
                    ))
            );
            var groundTruth = List.of(
                    fact("f1", "The king is alive"),
                    fact("f2", "The queen is wise")
            );
            var result = service.score(snapshots, groundTruth, List.of());
            // f1 first drifted at turn 2, f2 at turn 4 -> mean = 3.0
            assertThat(result.meanTurnsToFirstDrift()).isEqualTo(3.0);
        }

        @Test
        @DisplayName("empty verdicts list is not counted as an evaluated turn")
        void emptyVerdictsNotEvaluated() {
            var snapshots = List.of(
                    snapshot(1, null, List.of()),
                    snapshot(2, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.confirmed("f1", "ok")
                    ))
            );
            var groundTruth = List.of(fact("f1", "The king is alive"));
            var result = service.score(snapshots, groundTruth, List.of());
            // Only 1 evaluated turn, clean -> 100%
            assertThat(result.driftAbsorptionRate()).isEqualTo(100.0);
        }
    }

    @Nested
    @DisplayName("computeAttribution")
    class ComputeAttribution {

        @Test
        @DisplayName("exact text match counts as attribution")
        void exactMatchCounts() {
            var anchors = List.of(anchor("a1", "The king is alive"));
            var facts = List.of(fact("f1", "The king is alive"));
            assertThat(ScoringService.computeAttribution(anchors, facts)).isEqualTo(1);
        }

        @Test
        @DisplayName("substring match counts as attribution")
        void substringMatchCounts() {
            var anchors = List.of(anchor("a1", "The king is alive and well in the castle"));
            var facts = List.of(fact("f1", "The king is alive"));
            assertThat(ScoringService.computeAttribution(anchors, facts)).isEqualTo(1);
        }

        @Test
        @DisplayName("reverse substring match counts as attribution")
        void reverseSubstringMatchCounts() {
            var anchors = List.of(anchor("a1", "The king is alive and rules the land"));
            var facts = List.of(fact("f1", "king is alive"));
            // normalized anchor contains normalized fact
            assertThat(ScoringService.computeAttribution(anchors, facts)).isEqualTo(1);
        }

        @Test
        @DisplayName("no match returns zero")
        void noMatchReturnsZero() {
            var anchors = List.of(anchor("a1", "The queen is dead"));
            var facts = List.of(fact("f1", "The king is alive"));
            assertThat(ScoringService.computeAttribution(anchors, facts)).isZero();
        }

        @Test
        @DisplayName("case and punctuation differences still match")
        void normalizationHandlesCaseAndPunctuation() {
            var anchors = List.of(anchor("a1", "The King's Crown!"));
            var facts = List.of(fact("f1", "the kings crown"));
            assertThat(ScoringService.computeAttribution(anchors, facts)).isEqualTo(1);
        }

        @Test
        @DisplayName("multiple facts with partial anchor coverage")
        void multipleFactsPartialCoverage() {
            var anchors = List.of(
                    anchor("a1", "The king is alive"),
                    anchor("a2", "The sword is enchanted")
            );
            var facts = List.of(
                    fact("f1", "The king is alive"),
                    fact("f2", "The queen is wise"),
                    fact("f3", "The sword is enchanted")
            );
            assertThat(ScoringService.computeAttribution(anchors, facts)).isEqualTo(2);
        }
    }
}
