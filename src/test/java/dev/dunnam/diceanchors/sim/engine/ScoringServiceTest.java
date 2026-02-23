package dev.dunnam.diceanchors.sim.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScoringService")
class ScoringServiceTest {

    private final ScoringService service = new ScoringService();

    private static SimulationRunRecord.TurnSnapshot snapshot(int turnNumber,
                                                              AttackStrategy strategy,
                                                              List<EvalVerdict> verdicts) {
        return new SimulationRunRecord.TurnSnapshot(
                turnNumber, TurnType.ATTACK, strategy != null ? List.of(strategy) : List.of(),
                "player message", "dm response",
                List.of(), null, verdicts, true, null
        );
    }

    private static SimulationScenario.GroundTruth fact(String id, String text) {
        return new SimulationScenario.GroundTruth(id, text);
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
            var result = service.score(snapshots, groundTruth);
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
            var result = service.score(snapshots, groundTruth);
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
            var result = service.score(snapshots, groundTruth);
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
            var result = service.score(snapshots, groundTruth);
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
            var result = service.score(snapshots, groundTruth);
            // Only 1 evaluated turn, clean -> 100%
            assertThat(result.driftAbsorptionRate()).isEqualTo(100.0);
        }
    }

    @Nested
    @DisplayName("computeAttribution")
    class ComputeAttribution {

        @Test
        @DisplayName("counts distinct confirmed fact IDs across turns")
        void countsDistinctConfirmedFacts() {
            var snapshots = List.of(
                    snapshot(1, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.confirmed("f1", "ok")
                    )),
                    snapshot(2, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.confirmed("f1", "still ok"),
                            EvalVerdict.confirmed("f2", "also ok")
                    ))
            );
            assertThat(ScoringService.computeAttribution(snapshots)).isEqualTo(2);
        }

        @Test
        @DisplayName("contradicted-only facts are not counted")
        void contradictedNotCounted() {
            var snapshots = List.of(
                    snapshot(1, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.contradicted("f1", EvalVerdict.Severity.MAJOR, "drifted")
                    ))
            );
            assertThat(ScoringService.computeAttribution(snapshots)).isZero();
        }

        @Test
        @DisplayName("not-mentioned facts are not counted")
        void notMentionedNotCounted() {
            var snapshots = List.of(
                    snapshot(1, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.notMentioned("f1")
                    ))
            );
            assertThat(ScoringService.computeAttribution(snapshots)).isZero();
        }

        @Test
        @DisplayName("fact confirmed in one turn and contradicted in another still counts")
        void confirmedThenContradictedStillCounts() {
            var snapshots = List.of(
                    snapshot(1, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.confirmed("f1", "ok")
                    )),
                    snapshot(2, AttackStrategy.CONFIDENT_ASSERTION, List.of(
                            EvalVerdict.contradicted("f1", EvalVerdict.Severity.MINOR, "drifted")
                    ))
            );
            assertThat(ScoringService.computeAttribution(snapshots)).isEqualTo(1);
        }

        @Test
        @DisplayName("empty snapshots returns zero")
        void emptySnapshotsReturnsZero() {
            assertThat(ScoringService.computeAttribution(List.of())).isZero();
        }
    }
}
