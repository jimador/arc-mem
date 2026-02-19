package dev.dunnam.diceanchors.sim.assertions;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.PromotionZone;
import dev.dunnam.diceanchors.anchor.TrustScore;
import dev.dunnam.diceanchors.sim.engine.SimulationResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Simulation Assertions")
class AssertionTest {

    private static Anchor anchor(String id, String text, int rank, Authority authority) {
        return Anchor.withoutTrust(id, text, rank, authority, false, 0.9, 0);
    }

    private static Anchor anchorWithTrust(String id, String text, int rank, Authority authority,
                                          double score, PromotionZone zone) {
        var trust = new TrustScore(score, Authority.RELIABLE, zone, Map.of(), Instant.now());
        return new Anchor(id, text, rank, authority, false, 0.9, 0, trust, 0.0, 1.0);
    }

    private static SimulationResult result(List<Anchor> anchors) {
        return new SimulationResult("test-scenario", anchors, List.of(), List.of(), false);
    }

    private static SimulationResult resultWithGroundTruth(List<Anchor> anchors, List<String> groundTruth) {
        return new SimulationResult("test-scenario", anchors, List.of(), groundTruth, false);
    }

    @Nested
    @DisplayName("AnchorCountAssertion")
    class AnchorCountAssertionTests {

        @Test
        @DisplayName("passes when count is within [min, max]")
        void evaluateCountWithinRangePasses() {
            var assertion = new AnchorCountAssertion(Map.of("min", 1, "max", 5));
            var res = result(List.of(
                    anchor("a1", "fact one", 500, Authority.RELIABLE),
                    anchor("a2", "fact two", 600, Authority.RELIABLE)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isTrue();
            assertThat(outcome.name()).isEqualTo("anchor-count");
        }

        @Test
        @DisplayName("fails when count exceeds max")
        void evaluateCountExceedsMaxFails() {
            var assertion = new AnchorCountAssertion(Map.of("min", 1, "max", 1));
            var res = result(List.of(
                    anchor("a1", "fact one", 500, Authority.RELIABLE),
                    anchor("a2", "fact two", 600, Authority.RELIABLE)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isFalse();
        }

        @Test
        @DisplayName("fails when count is below min")
        void evaluateCountBelowMinFails() {
            var assertion = new AnchorCountAssertion(Map.of("min", 3, "max", 10));
            var res = result(List.of(anchor("a1", "fact one", 500, Authority.RELIABLE)));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isFalse();
        }

        @Test
        @DisplayName("passes with exact boundary counts")
        void evaluateCountExactBoundaryPasses() {
            var assertion = new AnchorCountAssertion(Map.of("min", 2, "max", 2));
            var res = result(List.of(
                    anchor("a1", "fact one", 500, Authority.RELIABLE),
                    anchor("a2", "fact two", 600, Authority.RELIABLE)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("RankDistributionAssertion")
    class RankDistributionAssertionTests {

        @Test
        @DisplayName("passes when enough anchors exceed rank threshold")
        void evaluateEnoughAboveThresholdPasses() {
            var assertion = new RankDistributionAssertion(Map.of("minAbove", 2, "rankThreshold", 400));
            var res = result(List.of(
                    anchor("a1", "fact one", 500, Authority.RELIABLE),
                    anchor("a2", "fact two", 700, Authority.RELIABLE),
                    anchor("a3", "fact three", 300, Authority.PROVISIONAL)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isTrue();
            assertThat(outcome.name()).isEqualTo("rank-distribution");
        }

        @Test
        @DisplayName("fails when too few anchors exceed rank threshold")
        void evaluateTooFewAboveThresholdFails() {
            var assertion = new RankDistributionAssertion(Map.of("minAbove", 3, "rankThreshold", 500));
            var res = result(List.of(
                    anchor("a1", "fact one", 600, Authority.RELIABLE),
                    anchor("a2", "fact two", 400, Authority.PROVISIONAL)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isFalse();
        }

        @Test
        @DisplayName("uses strictly greater than, not equal")
        void evaluateEqualToThresholdDoesNotCount() {
            var assertion = new RankDistributionAssertion(Map.of("minAbove", 1, "rankThreshold", 500));
            var res = result(List.of(anchor("a1", "fact one", 500, Authority.RELIABLE)));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isFalse();
        }
    }

    @Nested
    @DisplayName("TrustScoreRangeAssertion")
    class TrustScoreRangeAssertionTests {

        @Test
        @DisplayName("passes when all trust scores are within range")
        void evaluateAllWithinRangePasses() {
            var assertion = new TrustScoreRangeAssertion(Map.of("min", 0.3, "max", 0.9));
            var res = result(List.of(
                    anchorWithTrust("a1", "fact one", 500, Authority.RELIABLE, 0.5, PromotionZone.AUTO_PROMOTE),
                    anchorWithTrust("a2", "fact two", 600, Authority.RELIABLE, 0.8, PromotionZone.AUTO_PROMOTE)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isTrue();
            assertThat(outcome.name()).isEqualTo("trust-score-range");
        }

        @Test
        @DisplayName("fails when a trust score is below min")
        void evaluateScoreBelowMinFails() {
            var assertion = new TrustScoreRangeAssertion(Map.of("min", 0.5, "max", 1.0));
            var res = result(List.of(
                    anchorWithTrust("a1", "low trust", 500, Authority.RELIABLE, 0.2, PromotionZone.ARCHIVE)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isFalse();
        }

        @Test
        @DisplayName("fails when a trust score exceeds max")
        void evaluateScoreAboveMaxFails() {
            var assertion = new TrustScoreRangeAssertion(Map.of("min", 0.0, "max", 0.5));
            var res = result(List.of(
                    anchorWithTrust("a1", "high trust", 500, Authority.RELIABLE, 0.8, PromotionZone.AUTO_PROMOTE)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isFalse();
        }

        @Test
        @DisplayName("ignores anchors without trust scores")
        void evaluateNullTrustScoreIgnoredPasses() {
            var assertion = new TrustScoreRangeAssertion(Map.of("min", 0.5, "max", 1.0));
            var res = result(List.of(
                    anchor("a1", "no trust", 500, Authority.RELIABLE)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("PromotionZoneAssertion")
    class PromotionZoneAssertionTests {

        @Test
        @DisplayName("passes when enough anchors are in the expected zone")
        void evaluateEnoughInZonePasses() {
            var assertion = new PromotionZoneAssertion(Map.of("zone", "AUTO_PROMOTE", "minCount", 2));
            var res = result(List.of(
                    anchorWithTrust("a1", "fact one", 500, Authority.RELIABLE, 0.9, PromotionZone.AUTO_PROMOTE),
                    anchorWithTrust("a2", "fact two", 600, Authority.RELIABLE, 0.85, PromotionZone.AUTO_PROMOTE)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isTrue();
            assertThat(outcome.name()).isEqualTo("promotion-zone");
        }

        @Test
        @DisplayName("fails when too few anchors are in the expected zone")
        void evaluateTooFewInZoneFails() {
            var assertion = new PromotionZoneAssertion(Map.of("zone", "AUTO_PROMOTE", "minCount", 3));
            var res = result(List.of(
                    anchorWithTrust("a1", "fact one", 500, Authority.RELIABLE, 0.9, PromotionZone.AUTO_PROMOTE),
                    anchorWithTrust("a2", "fact two", 600, Authority.RELIABLE, 0.4, PromotionZone.REVIEW)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isFalse();
        }

        @Test
        @DisplayName("ignores anchors without trust scores")
        void evaluateNullTrustScoreNotCountedFails() {
            var assertion = new PromotionZoneAssertion(Map.of("zone", "REVIEW", "minCount", 1));
            var res = result(List.of(
                    anchor("a1", "no trust", 500, Authority.RELIABLE)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isFalse();
        }
    }

    @Nested
    @DisplayName("AuthorityAtMostAssertion")
    class AuthorityAtMostAssertionTests {

        @Test
        @DisplayName("passes when all anchors are at or below max authority")
        void evaluateAllAtOrBelowPasses() {
            var assertion = new AuthorityAtMostAssertion(Map.of("maxAuthority", "RELIABLE"));
            var res = result(List.of(
                    anchor("a1", "fact one", 500, Authority.RELIABLE),
                    anchor("a2", "fact two", 400, Authority.PROVISIONAL)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isTrue();
            assertThat(outcome.name()).isEqualTo("authority-at-most");
        }

        @Test
        @DisplayName("fails when an anchor exceeds max authority")
        void evaluateAnchorExceedsMaxFails() {
            var assertion = new AuthorityAtMostAssertion(Map.of("maxAuthority", "RELIABLE"));
            var res = result(List.of(
                    anchor("a1", "fact one", 500, Authority.CANON)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isFalse();
        }

        @Test
        @DisplayName("passes when authority equals the max exactly")
        void evaluateExactMaxAuthorityPasses() {
            var assertion = new AuthorityAtMostAssertion(Map.of("maxAuthority", "UNRELIABLE"));
            var res = result(List.of(
                    anchor("a1", "fact one", 500, Authority.UNRELIABLE)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("KgContextContainsAssertion")
    class KgContextContainsAssertionTests {

        @Test
        @DisplayName("passes when all patterns are found in anchor texts")
        void evaluateAllPatternsPresentPasses() {
            var assertion = new KgContextContainsAssertion(Map.of("patterns", List.of("dragon", "tavern")));
            var res = result(List.of(
                    anchor("a1", "A red Dragon guards the gate", 500, Authority.RELIABLE),
                    anchor("a2", "The Tavern is burning", 600, Authority.RELIABLE)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isTrue();
            assertThat(outcome.name()).isEqualTo("kg-context-contains");
        }

        @Test
        @DisplayName("fails when a pattern is missing from anchor texts")
        void evaluateMissingPatternFails() {
            var assertion = new KgContextContainsAssertion(Map.of("patterns", List.of("dragon", "lich")));
            var res = result(List.of(
                    anchor("a1", "A red Dragon guards the gate", 500, Authority.RELIABLE)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isFalse();
            assertThat(outcome.details()).contains("lich");
        }

        @Test
        @DisplayName("passes with empty patterns list")
        void evaluateEmptyPatternsPasses() {
            var assertion = new KgContextContainsAssertion(Map.of("patterns", List.of()));
            var res = result(List.of());

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("KgContextEmptyAssertion")
    class KgContextEmptyAssertionTests {

        @Test
        @DisplayName("passes when no anchors remain")
        void evaluateEmptyAnchorsPasses() {
            var assertion = new KgContextEmptyAssertion();
            var res = result(List.of());

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isTrue();
            assertThat(outcome.name()).isEqualTo("kg-context-empty");
        }

        @Test
        @DisplayName("fails when anchors are still present")
        void evaluateNonEmptyAnchorsFails() {
            var assertion = new KgContextEmptyAssertion();
            var res = result(List.of(
                    anchor("a1", "lingering fact", 500, Authority.RELIABLE)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isFalse();
        }
    }

    @Nested
    @DisplayName("NoCanonAutoAssignedAssertion")
    class NoCanonAutoAssignedAssertionTests {

        @Test
        @DisplayName("passes when no anchor has CANON authority")
        void evaluateNoCanonPasses() {
            var assertion = new NoCanonAutoAssignedAssertion();
            var res = result(List.of(
                    anchor("a1", "fact one", 500, Authority.RELIABLE),
                    anchor("a2", "fact two", 400, Authority.PROVISIONAL)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isTrue();
            assertThat(outcome.name()).isEqualTo("no-canon-auto-assigned");
        }

        @Test
        @DisplayName("fails when an anchor has CANON authority")
        void evaluateCanonPresentFails() {
            var assertion = new NoCanonAutoAssignedAssertion();
            var res = result(List.of(
                    anchor("a1", "fact one", 500, Authority.CANON)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isFalse();
            assertThat(outcome.details()).contains("a1");
        }

        @Test
        @DisplayName("passes with empty anchor list")
        void evaluateEmptyListPasses() {
            var assertion = new NoCanonAutoAssignedAssertion();
            var res = result(List.of());

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("CompactionIntegrityAssertion")
    class CompactionIntegrityAssertionTests {

        @Test
        @DisplayName("passes when all required facts are present in anchors")
        void evaluateAllFactsPresentPasses() {
            var assertion = new CompactionIntegrityAssertion(
                    Map.of("requiredFacts", List.of("cursed blade", "fire damage")));
            var res = result(List.of(
                    anchor("a1", "The Cursed Blade deals extra fire damage", 500, Authority.RELIABLE)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isTrue();
            assertThat(outcome.name()).isEqualTo("compaction-integrity");
        }

        @Test
        @DisplayName("fails when a required fact is missing after compaction")
        void evaluateMissingFactFails() {
            var assertion = new CompactionIntegrityAssertion(
                    Map.of("requiredFacts", List.of("cursed blade", "ancient rune")));
            var res = result(List.of(
                    anchor("a1", "The Cursed Blade deals fire damage", 500, Authority.RELIABLE)
            ));

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isFalse();
            assertThat(outcome.details()).contains("ancient rune");
        }

        @Test
        @DisplayName("passes with empty required facts list")
        void evaluateEmptyRequiredFactsPasses() {
            var assertion = new CompactionIntegrityAssertion(Map.of("requiredFacts", List.of()));
            var res = result(List.of());

            var outcome = assertion.evaluate(res);

            assertThat(outcome.passed()).isTrue();
        }
    }
}
