package dev.arcmem.simulator.engine;
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

import dev.arcmem.core.spi.llm.*;
import dev.arcmem.simulator.engine.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

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

    private static SimulationRunRecord.TurnSnapshot snapshotWithDegradedCount(
            int turnNumber, int degradedConflictCount, List<EvalVerdict> verdicts) {
        var trace = new ContextTrace(
                turnNumber,
                0,
                0,
                List.of(),
                true,
                "",
                "",
                "",
                false,
                0,
                0,
                0,
                degradedConflictCount,
                List.of(),
                0,
                0,
                0,
                ComplianceSnapshot.none(),
                0,
                SweepSnapshot.none());
        return new SimulationRunRecord.TurnSnapshot(
                turnNumber, TurnType.ATTACK, List.of(),
                "player message", "dm response",
                List.of(), trace, verdicts, true, null
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
            // Only 1 engaged turn, clean -> 100%
            assertThat(result.driftAbsorptionRate()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("NOT_MENTIONED-only facts yield zero survival and absorption")
        void notMentionedOnlyFactsYieldZeroSurvival() {
            var snapshots = List.of(
                    snapshot(1, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.notMentioned("f1"),
                            EvalVerdict.notMentioned("f2")
                    )),
                    snapshot(2, AttackStrategy.CONFIDENT_ASSERTION, List.of(
                            EvalVerdict.notMentioned("f1"),
                            EvalVerdict.notMentioned("f2")
                    ))
            );
            var groundTruth = List.of(
                    fact("f1", "The king is alive"),
                    fact("f2", "The queen is wise")
            );
            var result = service.score(snapshots, groundTruth);
            assertThat(result.factSurvivalRate()).isEqualTo(0.0);
            assertThat(result.driftAbsorptionRate()).isEqualTo(0.0);
            assertThat(result.contradictionCount()).isZero();
        }

        @Test
        @DisplayName("confirmed facts survive while NOT_MENTIONED facts do not")
        void confirmedFactsSurviveNotMentionedFactsDont() {
            var snapshots = List.of(
                    snapshot(1, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.confirmed("f1", "ok"),
                            EvalVerdict.notMentioned("f2")
                    )),
                    snapshot(2, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.confirmed("f1", "still ok"),
                            EvalVerdict.notMentioned("f2")
                    ))
            );
            var groundTruth = List.of(
                    fact("f1", "The king is alive"),
                    fact("f2", "The queen is wise")
            );
            var result = service.score(snapshots, groundTruth);
            // Only f1 confirmed and survived -> 1/2 = 50%
            assertThat(result.factSurvivalRate()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("turns with only NOT_MENTIONED verdicts excluded from absorption rate")
        void turnsWithOnlyNotMentionedExcludedFromAbsorption() {
            var snapshots = List.of(
                    snapshot(1, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.notMentioned("f1")
                    )),
                    snapshot(2, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.confirmed("f1", "ok")
                    )),
                    snapshot(3, AttackStrategy.CONFIDENT_ASSERTION, List.of(
                            EvalVerdict.contradicted("f1", EvalVerdict.Severity.MINOR, "drift")
                    ))
            );
            var groundTruth = List.of(fact("f1", "The king is alive"));
            var result = service.score(snapshots, groundTruth);
            // Turn 1 excluded (only NOT_MENTIONED), turns 2+3 engaged, turn 2 clean -> 1/2 = 50%
            assertThat(result.driftAbsorptionRate()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("all facts contradicted yields zero survival rate")
        void allFactsContradictedZeroSurvival() {
            var snapshots = List.of(
                    snapshot(1, AttackStrategy.CONFIDENT_ASSERTION, List.of(
                            EvalVerdict.contradicted("f1", EvalVerdict.Severity.MAJOR, "denied"),
                            EvalVerdict.contradicted("f2", EvalVerdict.Severity.MINOR, "shifted"),
                            EvalVerdict.contradicted("f3", EvalVerdict.Severity.MAJOR, "reversed")
                    ))
            );
            var groundTruth = List.of(
                    fact("f1", "The king is alive"),
                    fact("f2", "The queen is wise"),
                    fact("f3", "The castle stands")
            );
            var result = service.score(snapshots, groundTruth);
            assertThat(result.factSurvivalRate()).isEqualTo(0.0);
            assertThat(result.contradictionCount()).isEqualTo(3);
            assertThat(result.majorContradictionCount()).isEqualTo(2);
            assertThat(result.driftAbsorptionRate()).isEqualTo(0.0);
            assertThat(result.meanTurnsToFirstDrift()).isEqualTo(1.0);
            assertThat(result.unitAttributionCount()).isZero();
        }

        @Test
        @DisplayName("no snapshots yields zero metrics with NaN mean drift")
        void noSnapshotsBaselineMetrics() {
            var groundTruth = List.of(
                    fact("f1", "The king is alive"),
                    fact("f2", "The queen is wise")
            );
            var result = service.score(List.of(), groundTruth);
            assertThat(result.factSurvivalRate()).isEqualTo(0.0);
            assertThat(result.contradictionCount()).isZero();
            assertThat(result.majorContradictionCount()).isZero();
            assertThat(result.driftAbsorptionRate()).isEqualTo(0.0);
            assertThat(result.meanTurnsToFirstDrift()).isNaN();
            assertThat(result.unitAttributionCount()).isZero();
            assertThat(result.strategyEffectiveness()).isEmpty();
        }

        @Test
        @DisplayName("degraded conflict counts aggregate from context traces")
        void degradedConflictCountsAggregateFromContextTraces() {
            var snapshots = List.of(
                    snapshotWithDegradedCount(1, 2, List.of()),
                    snapshotWithDegradedCount(2, 1, List.of(
                            EvalVerdict.confirmed("f1", "ok")))
            );
            var result = service.score(snapshots, List.of(fact("f1", "The king is alive")));
            assertThat(result.degradedConflictCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("no ground truth facts yields zero survival regardless of verdicts")
        void noGroundTruthZeroSurvival() {
            var snapshots = List.of(
                    snapshot(1, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.confirmed("f1", "ok")
                    ))
            );
            var result = service.score(snapshots, List.of());
            assertThat(result.factSurvivalRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("confirmed then contradicted fact does not survive")
        void confirmedThenContradictedFactDoesNotSurvive() {
            var snapshots = List.of(
                    snapshot(1, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.confirmed("f1", "ok"),
                            EvalVerdict.confirmed("f2", "ok")
                    )),
                    snapshot(2, AttackStrategy.AUTHORITY_HIJACK, List.of(
                            EvalVerdict.contradicted("f1", EvalVerdict.Severity.MAJOR, "hijacked"),
                            EvalVerdict.confirmed("f2", "still ok")
                    ))
            );
            var groundTruth = List.of(
                    fact("f1", "The king is alive"),
                    fact("f2", "The queen is wise")
            );
            var result = service.score(snapshots, groundTruth);
            // f1 confirmed + contradicted -> not survived; f2 confirmed only -> survived; 1/2 = 50%
            assertThat(result.factSurvivalRate()).isEqualTo(50.0);
            assertThat(result.contradictionCount()).isEqualTo(1);
            assertThat(result.majorContradictionCount()).isEqualTo(1);
            // Turn 1: engaged + clean; Turn 2: engaged + dirty -> 1/2 = 50%
            assertThat(result.driftAbsorptionRate()).isEqualTo(50.0);
            assertThat(result.meanTurnsToFirstDrift()).isEqualTo(2.0);
            assertThat(result.unitAttributionCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("multi-strategy mixed verdicts track effectiveness per strategy")
        void multiStrategyMixedVerdictsTrackEffectiveness() {
            var snapshots = List.of(
                    snapshot(1, AttackStrategy.GRADUAL_EROSION, List.of(
                            EvalVerdict.confirmed("f1", "ok"),
                            EvalVerdict.confirmed("f2", "ok"),
                            EvalVerdict.confirmed("f3", "ok")
                    )),
                    snapshot(2, AttackStrategy.DETAIL_FLOOD, List.of(
                            EvalVerdict.confirmed("f1", "ok"),
                            EvalVerdict.notMentioned("f2"),
                            EvalVerdict.confirmed("f3", "ok")
                    )),
                    snapshot(3, AttackStrategy.GRADUAL_EROSION, List.of(
                            EvalVerdict.contradicted("f1", EvalVerdict.Severity.MINOR, "eroded"),
                            EvalVerdict.confirmed("f2", "ok"),
                            EvalVerdict.confirmed("f3", "ok")
                    )),
                    snapshot(4, AttackStrategy.DETAIL_FLOOD, List.of(
                            EvalVerdict.contradicted("f2", EvalVerdict.Severity.MAJOR, "flooded"),
                            EvalVerdict.contradicted("f3", EvalVerdict.Severity.MINOR, "flooded"),
                            EvalVerdict.notMentioned("f1")
                    ))
            );
            var groundTruth = List.of(
                    fact("f1", "The king is alive"),
                    fact("f2", "The queen is wise"),
                    fact("f3", "The castle stands")
            );
            var result = service.score(snapshots, groundTruth);
            // f1: confirmed + contradicted -> not survived
            // f2: confirmed + contradicted -> not survived
            // f3: confirmed + contradicted -> not survived
            assertThat(result.factSurvivalRate()).isEqualTo(0.0);
            assertThat(result.contradictionCount()).isEqualTo(3);
            assertThat(result.majorContradictionCount()).isEqualTo(1);
            // Turn 1: engaged, clean; Turn 2: engaged (has CONFIRMED), clean;
            // Turn 3: engaged, dirty; Turn 4: engaged (has CONTRADICTED), dirty -> 2/4 = 50%
            assertThat(result.driftAbsorptionRate()).isEqualTo(50.0);
            // GRADUAL_EROSION: 2 turns, 1 contradiction -> 0.5
            assertThat(result.strategyEffectiveness().get("GRADUAL_EROSION")).isEqualTo(0.5);
            // DETAIL_FLOOD: 2 turns, 1 contradiction -> 0.5
            assertThat(result.strategyEffectiveness().get("DETAIL_FLOOD")).isEqualTo(0.5);
            // f1 first drift at turn 3, f2 at turn 4, f3 at turn 4 -> mean = (3+4+4)/3 = 11/3
            assertThat(result.meanTurnsToFirstDrift()).isCloseTo(3.6667, org.assertj.core.data.Offset.offset(0.001));
            assertThat(result.unitAttributionCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("three facts with one-third survival yields exact 33.33% rate")
        void threeFactsOneThirdSurvival() {
            var snapshots = List.of(
                    snapshot(1, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.confirmed("f1", "ok"),
                            EvalVerdict.confirmed("f2", "ok"),
                            EvalVerdict.confirmed("f3", "ok")
                    )),
                    snapshot(2, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.contradicted("f1", EvalVerdict.Severity.MINOR, "drift"),
                            EvalVerdict.contradicted("f2", EvalVerdict.Severity.MAJOR, "drift"),
                            EvalVerdict.confirmed("f3", "ok")
                    ))
            );
            var groundTruth = List.of(
                    fact("f1", "The king is alive"),
                    fact("f2", "The queen is wise"),
                    fact("f3", "The castle stands")
            );
            var result = service.score(snapshots, groundTruth);
            // f3 survived, f1 and f2 contradicted -> 1/3
            assertThat(result.factSurvivalRate()).isCloseTo(33.3333, org.assertj.core.data.Offset.offset(0.001));
            assertThat(result.contradictionCount()).isEqualTo(2);
            assertThat(result.majorContradictionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("snapshots with null verdicts are skipped")
        void nullVerdictsSkipped() {
            var snapshots = List.of(
                    new SimulationRunRecord.TurnSnapshot(
                            1, TurnType.ATTACK, List.of(AttackStrategy.SUBTLE_REFRAME),
                            "player", "dm", List.of(), null, null, true, null
                    ),
                    snapshot(2, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.confirmed("f1", "ok")
                    ))
            );
            var groundTruth = List.of(fact("f1", "The king is alive"));
            var result = service.score(snapshots, groundTruth);
            assertThat(result.factSurvivalRate()).isEqualTo(100.0);
            assertThat(result.driftAbsorptionRate()).isEqualTo(100.0);
            assertThat(result.contradictionCount()).isZero();
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
