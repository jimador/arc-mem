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

/**
 * Deterministic tests that validate unit engine behavior using known inputs
 * and canned responses, without requiring a running Spring context or LLM.
 * <p>
 * Each nested class exercises a specific unit mechanic (conflict detection,
 * resolution, reinforcement, scoring) by feeding known data into engine
 * components and asserting that output metrics satisfy operator expressions
 * via {@link MetricsValidator}.
 */
@DisplayName("Deterministic Simulation Tests")
class DeterministicSimulationTest {

    private final NegationConflictDetector conflictDetector = new NegationConflictDetector();
    private final ConflictResolver conflictResolver = ConflictResolver.byAuthority();
    private final ReinforcementPolicy reinforcementPolicy = ReinforcementPolicy.threshold();
    private final ScoringService scoringService = new ScoringService();

    // ========================================================================
    // Helpers
    // ========================================================================

    private static MemoryUnit unit(String id, String text, int rank, Authority authority) {
        return MemoryUnit.withoutTrust(id, text, rank, authority, false, 0.9, 0);
    }

    private static MemoryUnit unit(String id, String text, int rank, Authority authority, int reinforcementCount) {
        return MemoryUnit.withoutTrust(id, text, rank, authority, false, 0.9, reinforcementCount);
    }

    private static SimulationScenario.GroundTruth fact(String id, String text) {
        return new SimulationScenario.GroundTruth(id, text);
    }

    private static SimulationRunRecord.TurnSnapshot snapshot(int turnNumber,
                                                              AttackStrategy strategy,
                                                              List<EvalVerdict> verdicts) {
        return new SimulationRunRecord.TurnSnapshot(
                turnNumber, TurnType.ATTACK, strategy != null ? List.of(strategy) : List.of(),
                "player message", "dm response",
                List.of(), null, verdicts, true, null
        );
    }

    // ========================================================================
    // Scenario 1: Resist Direct Negation
    // ========================================================================

    @Nested
    @DisplayName("Resist Direct Negation")
    class ResistDirectNegation {

        @Test
        @DisplayName("negation of CANON fact triggers conflict detection with KEEP resolution")
        void negationOfCanonFactDetectedAndKept() {
            var canonUnit = unit("a1", "The king is alive and well", 800, Authority.CANON);
            var negation = "The king is not alive";

            var conflicts = conflictDetector.detect(negation, List.of(canonUnit));

            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.getFirst().existing().id()).isEqualTo("a1");
            assertThat(conflicts.getFirst().existing().authority()).isEqualTo(Authority.CANON);

            var resolution = conflictResolver.resolve(conflicts.getFirst());
            assertThat(resolution).isEqualTo(ConflictResolver.Resolution.KEEP_EXISTING);
        }

        @Test
        @DisplayName("scoring reflects zero contradictions when memory unit resists negation")
        void scoringReflectsSuccessfulResistance() {
            var groundTruth = List.of(fact("f1", "The king is alive"));
            var units = List.of(unit("a1", "The king is alive", 800, Authority.CANON));

            // Simulate turns where the DM correctly resists the negation
            var snapshots = List.of(
                    snapshot(1, AttackStrategy.CONFIDENT_ASSERTION, List.of(
                            EvalVerdict.confirmed("f1", "The DM affirmed the king is alive")
                    )),
                    snapshot(2, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.confirmed("f1", "The DM maintained the king is alive")
                    ))
            );

            var result = scoringService.score(snapshots, groundTruth);

            var validation = MetricsValidator.validateAll(List.of(
                    new MetricsValidator.MetricCheck("factSurvivalRate", result.factSurvivalRate(), ">= 100.0"),
                    new MetricsValidator.MetricCheck("contradictionCount", result.contradictionCount(), "== 0"),
                    new MetricsValidator.MetricCheck("driftAbsorptionRate", result.driftAbsorptionRate(), ">= 100.0")
            ));
            assertThat(validation.allPassed())
                    .withFailMessage(validation::failureSummary)
                    .isTrue();
        }

        @Test
        @DisplayName("negation of RELIABLE fact also triggers conflict and KEEP resolution")
        void negationOfReliableFactAlsoKept() {
            var reliableUnit = unit("a2", "The queen is wise and just", 600, Authority.RELIABLE);
            var negation = "The queen is not wise";

            var conflicts = conflictDetector.detect(negation, List.of(reliableUnit));
            assertThat(conflicts).isNotEmpty();

            var resolution = conflictResolver.resolve(conflicts.getFirst());
            assertThat(resolution).isEqualTo(ConflictResolver.Resolution.KEEP_EXISTING);
        }
    }

    // ========================================================================
    // Scenario 2: Resist Semantic Opposition
    // ========================================================================

    @Nested
    @DisplayName("Resist Semantic Opposition")
    class ResistSemanticOpposition {

        @Test
        @DisplayName("negation-based semantic opposition detected between opposing statements")
        void semanticOppositionDetected() {
            var units = List.of(
                    unit("a1", "The dragon is friendly to villagers", 700, Authority.RELIABLE)
            );
            // "not friendly" triggers negation detection with overlapping content words
            var opposition = "The dragon is not friendly to villagers";

            var conflicts = conflictDetector.detect(opposition, units);
            assertThat(conflicts).isNotEmpty();
            assertThat(conflicts.getFirst().confidence()).isGreaterThan(0.5);
        }

        @Test
        @DisplayName("scoring reflects survival when opposition is resisted")
        void scoringReflectsOppositionResisted() {
            var groundTruth = List.of(
                    fact("f1", "The dragon is friendly"),
                    fact("f2", "The enchanted forest is safe")
            );
            var units = List.of(
                    unit("a1", "The dragon is friendly", 700, Authority.RELIABLE),
                    unit("a2", "The enchanted forest is safe", 600, Authority.RELIABLE)
            );

            // All turns resist the opposition
            var snapshots = List.of(
                    snapshot(1, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.confirmed("f1", "Dragon remains friendly"),
                            EvalVerdict.confirmed("f2", "Forest still safe")
                    )),
                    snapshot(2, AttackStrategy.EMOTIONAL_OVERRIDE, List.of(
                            EvalVerdict.confirmed("f1", "Dragon stays friendly"),
                            EvalVerdict.confirmed("f2", "Forest confirmed safe")
                    ))
            );

            var result = scoringService.score(snapshots, groundTruth);

            var validation = MetricsValidator.validateAll(List.of(
                    new MetricsValidator.MetricCheck("factSurvivalRate", result.factSurvivalRate(), ">= 90.0"),
                    new MetricsValidator.MetricCheck("contradictionCount", result.contradictionCount(), "== 0"),
                    new MetricsValidator.MetricCheck("unitAttributionCount", result.unitAttributionCount(), ">= 2")
            ));
            assertThat(validation.allPassed())
                    .withFailMessage(validation::failureSummary)
                    .isTrue();
        }

        @Test
        @DisplayName("unrelated statements produce no spurious conflicts")
        void unrelatedStatementsNoSpuriousConflicts() {
            var units = List.of(
                    unit("a1", "The tavern serves excellent mead", 500, Authority.RELIABLE)
            );
            var unrelated = "The blacksmith forges a new sword";

            var conflicts = conflictDetector.detect(unrelated, units);
            assertThat(conflicts).isEmpty();
        }
    }

    // ========================================================================
    // Scenario 3: Rank Stability Under Reinforcement
    // ========================================================================

    @Nested
    @DisplayName("Rank Stability Under Reinforcement")
    class RankStabilityUnderReinforcement {

        @Test
        @DisplayName("reinforcement boosts rank by fixed increment")
        void reinforcementBoostsRank() {
            var unit = unit("a1", "The king is alive", 500, Authority.PROVISIONAL, 0);
            var boost = reinforcementPolicy.calculateRankBoost(unit);
            assertThat(boost).isEqualTo(50);
            var newRank = MemoryUnit.clampRank(unit.rank() + boost);
            assertThat(newRank).isEqualTo(550);
        }

        @Test
        @DisplayName("rank remains within clamped bounds after multiple reinforcements")
        void rankClampedAfterMultipleReinforcements() {
            var rank = 800;
            for (var i = 0; i < 10; i++) {
                var unit = unit("a1", "The king is alive", rank, Authority.RELIABLE, i);
                var boost = reinforcementPolicy.calculateRankBoost(unit);
                rank = MemoryUnit.clampRank(rank + boost);
            }
            assertThat(rank).isLessThanOrEqualTo(MemoryUnit.MAX_RANK);
            assertThat(rank).isEqualTo(MemoryUnit.MAX_RANK);
        }

        @Test
        @DisplayName("authority upgrades at threshold reinforcement counts")
        void authorityUpgradesAtThresholds() {
            // PROVISIONAL at 3 reinforcements -> should upgrade to UNRELIABLE
            var provisionalAt3 = unit("a1", "fact", 500, Authority.PROVISIONAL, 3);
            assertThat(reinforcementPolicy.shouldUpgradeAuthority(provisionalAt3)).isTrue();

            // PROVISIONAL at 2 reinforcements -> not yet
            var provisionalAt2 = unit("a1", "fact", 500, Authority.PROVISIONAL, 2);
            assertThat(reinforcementPolicy.shouldUpgradeAuthority(provisionalAt2)).isFalse();

            // UNRELIABLE at 7 reinforcements -> should upgrade to RELIABLE
            var unreliableAt7 = unit("a1", "fact", 600, Authority.UNRELIABLE, 7);
            assertThat(reinforcementPolicy.shouldUpgradeAuthority(unreliableAt7)).isTrue();

            // UNRELIABLE at 6 reinforcements -> not yet
            var unreliableAt6 = unit("a1", "fact", 600, Authority.UNRELIABLE, 6);
            assertThat(reinforcementPolicy.shouldUpgradeAuthority(unreliableAt6)).isFalse();

            // CANON never upgrades
            var canonAt100 = unit("a1", "fact", 900, Authority.CANON, 100);
            assertThat(reinforcementPolicy.shouldUpgradeAuthority(canonAt100)).isFalse();
        }

        @Test
        @DisplayName("scoring confirms rank stability via consistent confirmations")
        void scoringConfirmsRankStability() {
            var groundTruth = List.of(
                    fact("f1", "The enchanted sword glows"),
                    fact("f2", "The castle stands strong")
            );

            // All turns confirm facts, simulating monotonic rank increase
            var snapshots = List.of(
                    snapshot(1, AttackStrategy.SUBTLE_REFRAME, List.of(
                            EvalVerdict.confirmed("f1", "Confirmed"),
                            EvalVerdict.confirmed("f2", "Confirmed")
                    )),
                    snapshot(2, AttackStrategy.CONFIDENT_ASSERTION, List.of(
                            EvalVerdict.confirmed("f1", "Still confirmed"),
                            EvalVerdict.confirmed("f2", "Still confirmed")
                    )),
                    snapshot(3, AttackStrategy.AUTHORITY_HIJACK, List.of(
                            EvalVerdict.confirmed("f1", "Resisted hijack"),
                            EvalVerdict.confirmed("f2", "Resisted hijack")
                    ))
            );

            var result = scoringService.score(snapshots, groundTruth);

            var validation = MetricsValidator.validateAll(List.of(
                    new MetricsValidator.MetricCheck("factSurvivalRate", result.factSurvivalRate(), "== 100.0"),
                    new MetricsValidator.MetricCheck("contradictionCount", result.contradictionCount(), "== 0"),
                    new MetricsValidator.MetricCheck("driftAbsorptionRate", result.driftAbsorptionRate(), "== 100.0")
            ));
            assertThat(validation.allPassed())
                    .withFailMessage(validation::failureSummary)
                    .isTrue();
        }
    }

    // ========================================================================
    // Scenario 4: Authority Enforces Preservation
    // ========================================================================

    @Nested
    @DisplayName("Authority Enforces Preservation")
    class AuthorityEnforcesPreservation {

        @Test
        @DisplayName("CANON memory unit always resolved as KEEP against any conflict")
        void canonAlwaysKept() {
            var canonUnit = unit("a1", "The ancient treaty is binding", 900, Authority.CANON);
            var conflict = new ConflictDetector.Conflict(
                    canonUnit,
                    "The ancient treaty is not binding",
                    0.95,
                    "Direct negation"
            );

            var resolution = conflictResolver.resolve(conflict);
            assertThat(resolution).isEqualTo(ConflictResolver.Resolution.KEEP_EXISTING);
        }

        @Test
        @DisplayName("RELIABLE memory unit also resolved as KEEP")
        void reliableAlsoKept() {
            var reliableUnit = unit("a2", "The river flows north", 700, Authority.RELIABLE);
            var conflict = new ConflictDetector.Conflict(
                    reliableUnit,
                    "The river does not flow north",
                    0.9,
                    "Negation conflict"
            );

            var resolution = conflictResolver.resolve(conflict);
            assertThat(resolution).isEqualTo(ConflictResolver.Resolution.KEEP_EXISTING);
        }

        @Test
        @DisplayName("PROVISIONAL memory unit with high-confidence challenger is REPLACED")
        void provisionalReplacedByHighConfidence() {
            var provisionalUnit = unit("a3", "The merchant is honest", 300, Authority.PROVISIONAL);
            var conflict = new ConflictDetector.Conflict(
                    provisionalUnit,
                    "The merchant is not honest",
                    0.85,
                    "Negation conflict"
            );

            var resolution = conflictResolver.resolve(conflict);
            assertThat(resolution).isEqualTo(ConflictResolver.Resolution.REPLACE);
        }

        @Test
        @DisplayName("PROVISIONAL memory unit with low-confidence challenger COEXISTs")
        void provisionalCoexistsWithLowConfidence() {
            var provisionalUnit = unit("a4", "The path is safe", 300, Authority.PROVISIONAL);
            var conflict = new ConflictDetector.Conflict(
                    provisionalUnit,
                    "The path is not safe",
                    0.5,
                    "Negation conflict"
            );

            var resolution = conflictResolver.resolve(conflict);
            assertThat(resolution).isEqualTo(ConflictResolver.Resolution.COEXIST);
        }

        @Test
        @DisplayName("mixed authority scoring: CANON survives, PROVISIONAL may drift")
        void mixedAuthorityScoringCanonSurvivesProvisionalDrifts() {
            var groundTruth = List.of(
                    fact("f1", "The ancient treaty is binding"),
                    fact("f2", "The merchant is honest")
            );
            var units = List.of(
                    unit("a1", "The ancient treaty is binding", 900, Authority.CANON),
                    unit("a2", "The merchant is honest", 300, Authority.PROVISIONAL)
            );

            // CANON fact always confirmed; PROVISIONAL fact contradicted
            var snapshots = List.of(
                    snapshot(1, AttackStrategy.AUTHORITY_HIJACK, List.of(
                            EvalVerdict.confirmed("f1", "Treaty upheld"),
                            EvalVerdict.contradicted("f2", EvalVerdict.Severity.MAJOR, "Merchant exposed as dishonest")
                    )),
                    snapshot(2, AttackStrategy.CONFIDENT_ASSERTION, List.of(
                            EvalVerdict.confirmed("f1", "Treaty still binding"),
                            EvalVerdict.contradicted("f2", EvalVerdict.Severity.MINOR, "Merchant reputation questioned")
                    ))
            );

            var result = scoringService.score(snapshots, groundTruth);

            // f1 survived, f2 did not -> 50% survival
            var validation = MetricsValidator.validateAll(List.of(
                    new MetricsValidator.MetricCheck("factSurvivalRate", result.factSurvivalRate(), "== 50.0"),
                    new MetricsValidator.MetricCheck("contradictionCount", result.contradictionCount(), "== 2"),
                    new MetricsValidator.MetricCheck("majorContradictionCount", result.majorContradictionCount(), "== 1"),
                    new MetricsValidator.MetricCheck("unitAttributionCount", result.unitAttributionCount(), ">= 1")
            ));
            assertThat(validation.allPassed())
                    .withFailMessage(validation::failureSummary)
                    .isTrue();
        }
    }
}
