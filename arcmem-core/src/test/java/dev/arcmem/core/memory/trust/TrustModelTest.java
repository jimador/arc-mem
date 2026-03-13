package dev.arcmem.core.memory.trust;
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

import com.embabel.dice.proposition.PropositionStatus;
import dev.arcmem.core.persistence.PropositionNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DisplayName("Trust Model")
@ExtendWith(MockitoExtension.class)
class TrustModelTest {

    private static final String CONTEXT_ID = "test-context";

    private PropositionNode proposition(String text, double confidence) {
        return new PropositionNode(UUID.randomUUID().toString(), "test-context", text, confidence, 0.0, null, List.of(),
                Instant.now(), Instant.now(), PropositionStatus.ACTIVE, null, List.of());
    }

    private PropositionNode propositionWithSources(String text, double confidence, List<String> sourceIds) {
        return new PropositionNode(UUID.randomUUID().toString(), "test-context", text, confidence, 0.0, null, List.of(),
                Instant.now(), Instant.now(), PropositionStatus.ACTIVE, null, sourceIds);
    }

    // ========================================================================
    // TrustEvaluator
    // ========================================================================

    @Nested
    @DisplayName("TrustEvaluator")
    class TrustEvaluatorTests {

        @Nested
        @DisplayName("all signals present")
        class AllSignalsPresent {

            @Test
            @DisplayName("computes weighted sum from all signals using BALANCED profile")
            void evaluate_allSignalsPresent_computesWeightedSum() {
                // Quality signals absent (disabled) — weight redistributes to 4 core signals (0.22 each -> 0.25 each)
                var signals = List.<TrustSignal>of(
                        namedSignal("sourceAuthority", 0.9),
                        namedSignal("extractionConfidence", 0.8),
                        namedSignal("graphConsistency", 0.7),
                        namedSignal("corroboration", 0.6),
                        absentSignal("novelty"),
                        absentSignal("importance")
                );
                var evaluator = new TrustEvaluator(signals, DomainProfile.BALANCED);
                var prop = proposition("The dragon is red", 0.8);

                var result = evaluator.evaluate(prop, CONTEXT_ID);

                // BALANCED: core weights 0.22 each, quality absent (0.06+0.06) redistributes -> effective 0.25 each
                // Score = 0.25*0.9 + 0.25*0.8 + 0.25*0.7 + 0.25*0.6 = 0.75
                assertThat(result.score()).isCloseTo(0.75, within(0.001));
            }

            @Test
            @DisplayName("includes all signal names in audit map")
            void evaluate_allSignalsPresent_auditContainsAllSignals() {
                var signals = List.<TrustSignal>of(
                        namedSignal("sourceAuthority", 0.9),
                        namedSignal("extractionConfidence", 0.8),
                        namedSignal("graphConsistency", 0.7),
                        namedSignal("corroboration", 0.6)
                );
                var evaluator = new TrustEvaluator(signals, DomainProfile.BALANCED);
                var prop = proposition("The dragon is red", 0.8);

                var result = evaluator.evaluate(prop, CONTEXT_ID);

                assertThat(result.signalAudit()).containsKeys(
                        "sourceAuthority", "extractionConfidence", "graphConsistency", "corroboration");
                assertThat(result.signalAudit().get("sourceAuthority")).isCloseTo(0.9, within(0.001));
            }

            @Test
            @DisplayName("sets evaluatedAt timestamp")
            void evaluate_allSignalsPresent_setsEvaluatedAt() {
                var signals = List.<TrustSignal>of(namedSignal("sourceAuthority", 0.5));
                var evaluator = new TrustEvaluator(signals, DomainProfile.BALANCED);
                var prop = proposition("test", 0.5);

                var result = evaluator.evaluate(prop, CONTEXT_ID);

                assertThat(result.evaluatedAt()).isNotNull();
            }
        }

        @Nested
        @DisplayName("absent signal redistribution")
        class AbsentSignalRedistribution {

            @Test
            @DisplayName("redistributes absent signal weight to present signals")
            void evaluate_oneSignalAbsent_redistributesWeight() {
                // corroboration, novelty, importance all absent — weight redistributes to 3 present signals
                var signals = List.<TrustSignal>of(
                        namedSignal("sourceAuthority", 0.8),
                        namedSignal("extractionConfidence", 0.8),
                        namedSignal("graphConsistency", 0.8),
                        absentSignal("corroboration"),
                        absentSignal("novelty"),
                        absentSignal("importance")
                );
                var evaluator = new TrustEvaluator(signals, DomainProfile.BALANCED);
                var prop = proposition("The goblin is green", 0.8);

                var result = evaluator.evaluate(prop, CONTEXT_ID);

                // Present weight = 0.66 (3 * 0.22), absent weight = 0.34 (0.22 + 0.06 + 0.06)
                // Redistribution factor = 1.0 / 0.66 = 1.5151...
                // Each present signal: 0.8 * 0.22 * 1.5151 = 0.8 * 0.3333 = 0.2666...
                // Total = 3 * 0.2666... = 0.8
                assertThat(result.score()).isCloseTo(0.8, within(0.001));
            }

            @Test
            @DisplayName("absent signal not included in audit map")
            void evaluate_oneSignalAbsent_auditExcludesAbsentSignal() {
                var signals = List.<TrustSignal>of(
                        namedSignal("sourceAuthority", 0.8),
                        absentSignal("corroboration")
                );
                var evaluator = new TrustEvaluator(signals, DomainProfile.BALANCED);
                var prop = proposition("test", 0.5);

                var result = evaluator.evaluate(prop, CONTEXT_ID);

                assertThat(result.signalAudit()).containsKey("sourceAuthority");
                assertThat(result.signalAudit()).doesNotContainKey("corroboration");
            }

            @Test
            @DisplayName("all signals absent produces score of zero")
            void evaluate_allSignalsAbsent_scoresZero() {
                var signals = List.<TrustSignal>of(
                        absentSignal("sourceAuthority"),
                        absentSignal("extractionConfidence"),
                        absentSignal("graphConsistency"),
                        absentSignal("corroboration")
                );
                var evaluator = new TrustEvaluator(signals, DomainProfile.BALANCED);
                var prop = proposition("test", 0.5);

                var result = evaluator.evaluate(prop, CONTEXT_ID);

                assertThat(result.score()).isCloseTo(0.0, within(0.001));
            }
        }

        @Nested
        @DisplayName("authority ceiling")
        class AuthorityCeiling {

            @Test
            @DisplayName("score >= 0.80 yields RELIABLE ceiling")
            void evaluate_highScore_ceilingIsReliable() {
                var signals = List.<TrustSignal>of(
                        namedSignal("sourceAuthority", 1.0),
                        namedSignal("extractionConfidence", 1.0),
                        namedSignal("graphConsistency", 1.0),
                        namedSignal("corroboration", 1.0)
                );
                var evaluator = new TrustEvaluator(signals, DomainProfile.BALANCED);
                var prop = proposition("test", 0.9);

                var result = evaluator.evaluate(prop, CONTEXT_ID);

                assertThat(result.score()).isGreaterThanOrEqualTo(0.80);
                assertThat(result.authorityCeiling()).isEqualTo(Authority.RELIABLE);
            }

            @Test
            @DisplayName("score exactly 0.80 yields RELIABLE ceiling")
            void evaluate_scoreExactly080_ceilingIsReliable() {
                // Core weights 0.22 each; quality signals absent, redistribute -> effective 0.25 each -> score = 0.8
                var signals = List.<TrustSignal>of(
                        namedSignal("sourceAuthority", 0.8),
                        namedSignal("extractionConfidence", 0.8),
                        namedSignal("graphConsistency", 0.8),
                        namedSignal("corroboration", 0.8),
                        absentSignal("novelty"),
                        absentSignal("importance")
                );
                var evaluator = new TrustEvaluator(signals, DomainProfile.BALANCED);
                var prop = proposition("test", 0.8);

                var result = evaluator.evaluate(prop, CONTEXT_ID);

                assertThat(result.score()).isCloseTo(0.80, within(0.001));
                assertThat(result.authorityCeiling()).isEqualTo(Authority.RELIABLE);
            }

            @Test
            @DisplayName("score in [0.50, 0.80) yields UNRELIABLE ceiling")
            void evaluate_midScore_ceilingIsUnreliable() {
                // Core weights 0.22 each; quality signals absent, redistribute -> effective 0.25 each -> score = 0.6
                var signals = List.<TrustSignal>of(
                        namedSignal("sourceAuthority", 0.6),
                        namedSignal("extractionConfidence", 0.6),
                        namedSignal("graphConsistency", 0.6),
                        namedSignal("corroboration", 0.6),
                        absentSignal("novelty"),
                        absentSignal("importance")
                );
                var evaluator = new TrustEvaluator(signals, DomainProfile.BALANCED);
                var prop = proposition("test", 0.6);

                var result = evaluator.evaluate(prop, CONTEXT_ID);

                assertThat(result.score()).isCloseTo(0.60, within(0.001));
                assertThat(result.authorityCeiling()).isEqualTo(Authority.UNRELIABLE);
            }

            @Test
            @DisplayName("score exactly 0.50 yields UNRELIABLE ceiling")
            void evaluate_scoreExactly050_ceilingIsUnreliable() {
                // Core weights 0.22 each; quality signals absent, redistribute -> effective 0.25 each -> score = 0.5
                var signals = List.<TrustSignal>of(
                        namedSignal("sourceAuthority", 0.5),
                        namedSignal("extractionConfidence", 0.5),
                        namedSignal("graphConsistency", 0.5),
                        namedSignal("corroboration", 0.5),
                        absentSignal("novelty"),
                        absentSignal("importance")
                );
                var evaluator = new TrustEvaluator(signals, DomainProfile.BALANCED);
                var prop = proposition("test", 0.5);

                var result = evaluator.evaluate(prop, CONTEXT_ID);

                assertThat(result.score()).isCloseTo(0.50, within(0.001));
                assertThat(result.authorityCeiling()).isEqualTo(Authority.UNRELIABLE);
            }

            @Test
            @DisplayName("score < 0.50 yields PROVISIONAL ceiling")
            void evaluate_lowScore_ceilingIsProvisional() {
                var signals = List.<TrustSignal>of(
                        namedSignal("sourceAuthority", 0.3),
                        namedSignal("extractionConfidence", 0.3),
                        namedSignal("graphConsistency", 0.3),
                        namedSignal("corroboration", 0.3)
                );
                var evaluator = new TrustEvaluator(signals, DomainProfile.BALANCED);
                var prop = proposition("test", 0.3);

                var result = evaluator.evaluate(prop, CONTEXT_ID);

                assertThat(result.score()).isLessThan(0.50);
                assertThat(result.authorityCeiling()).isEqualTo(Authority.PROVISIONAL);
            }

            @Test
            @DisplayName("CANON is never assigned as ceiling (invariant A3)")
            void evaluate_perfectScore_neverAssignsCanon() {
                // Core weights 0.22 each; quality signals absent, redistribute -> effective 0.25 each -> score = 1.0
                var signals = List.<TrustSignal>of(
                        namedSignal("sourceAuthority", 1.0),
                        namedSignal("extractionConfidence", 1.0),
                        namedSignal("graphConsistency", 1.0),
                        namedSignal("corroboration", 1.0),
                        absentSignal("novelty"),
                        absentSignal("importance")
                );
                var evaluator = new TrustEvaluator(signals, DomainProfile.BALANCED);
                var prop = proposition("test", 1.0);

                var result = evaluator.evaluate(prop, CONTEXT_ID);

                assertThat(result.score()).isCloseTo(1.0, within(0.001));
                assertThat(result.authorityCeiling()).isNotEqualTo(Authority.CANON);
                assertThat(result.authorityCeiling()).isEqualTo(Authority.RELIABLE);
            }
        }

        @Nested
        @DisplayName("zone routing")
        class ZoneRouting {

            @Test
            @DisplayName("score above autoPromoteThreshold routes to AUTO_PROMOTE")
            void evaluate_aboveAutoPromote_routesToAutoPromote() {
                // BALANCED autoPromoteThreshold = 0.65
                var signals = List.<TrustSignal>of(
                        namedSignal("sourceAuthority", 0.9),
                        namedSignal("extractionConfidence", 0.9),
                        namedSignal("graphConsistency", 0.9),
                        namedSignal("corroboration", 0.9)
                );
                var evaluator = new TrustEvaluator(signals, DomainProfile.BALANCED);
                var prop = proposition("test", 0.9);

                var result = evaluator.evaluate(prop, CONTEXT_ID);

                assertThat(result.promotionZone()).isEqualTo(PromotionZone.AUTO_PROMOTE);
            }

            @Test
            @DisplayName("score between review and autoPromote routes to REVIEW")
            void evaluate_betweenReviewAndAutoPromote_routesToReview() {
                // BALANCED: autoPromote=0.65, review=0.40
                var signals = List.<TrustSignal>of(
                        namedSignal("sourceAuthority", 0.5),
                        namedSignal("extractionConfidence", 0.5),
                        namedSignal("graphConsistency", 0.5),
                        namedSignal("corroboration", 0.5)
                );
                var evaluator = new TrustEvaluator(signals, DomainProfile.BALANCED);
                var prop = proposition("test", 0.5);

                var result = evaluator.evaluate(prop, CONTEXT_ID);

                assertThat(result.promotionZone()).isEqualTo(PromotionZone.REVIEW);
            }

            @Test
            @DisplayName("score below reviewThreshold routes to ARCHIVE")
            void evaluate_belowReview_routesToArchive() {
                // BALANCED: review=0.40
                var signals = List.<TrustSignal>of(
                        namedSignal("sourceAuthority", 0.1),
                        namedSignal("extractionConfidence", 0.1),
                        namedSignal("graphConsistency", 0.1),
                        namedSignal("corroboration", 0.1)
                );
                var evaluator = new TrustEvaluator(signals, DomainProfile.BALANCED);
                var prop = proposition("test", 0.1);

                var result = evaluator.evaluate(prop, CONTEXT_ID);

                assertThat(result.promotionZone()).isEqualTo(PromotionZone.ARCHIVE);
            }
        }

        @Nested
        @DisplayName("score clamping")
        class ScoreClamping {

            @Test
            @DisplayName("score is clamped to [0.0, 1.0]")
            void evaluate_signalValues_clampedToUnitInterval() {
                // Even with redistribution, score stays in [0, 1]
                var signals = List.<TrustSignal>of(namedSignal("sourceAuthority", 1.0));
                var evaluator = new TrustEvaluator(signals, DomainProfile.BALANCED);
                var prop = proposition("test", 1.0);

                var result = evaluator.evaluate(prop, CONTEXT_ID);

                assertThat(result.score()).isBetween(0.0, 1.0);
            }
        }

        @Nested
        @DisplayName("profile-specific weights")
        class ProfileSpecificWeights {

            @Test
            @DisplayName("SECURE profile weights graphConsistency heavily")
            void evaluate_secureProfile_weightsGraphHeavily() {
                var signals = List.<TrustSignal>of(
                        namedSignal("sourceAuthority", 0.5),
                        namedSignal("extractionConfidence", 0.5),
                        namedSignal("graphConsistency", 1.0),
                        namedSignal("corroboration", 0.5)
                );
                var balanced = new TrustEvaluator(signals, DomainProfile.BALANCED);
                var secure = new TrustEvaluator(signals, DomainProfile.SECURE);
                var prop = proposition("test", 0.5);

                var balancedResult = balanced.evaluate(prop, CONTEXT_ID);
                var secureResult = secure.evaluate(prop, CONTEXT_ID);

                // SECURE has graphConsistency weight 0.4 vs BALANCED 0.25,
                // so with graphConsistency=1.0 and others=0.5, SECURE should score higher
                assertThat(secureResult.score()).isGreaterThan(balancedResult.score());
            }
        }
    }

    // ========================================================================
    // ExtractionConfidenceSignal
    // ========================================================================

    @Nested
    @DisplayName("ExtractionConfidenceSignal")
    class ExtractionConfidenceSignalTests {

        private final TrustSignal signal = TrustSignal.extractionConfidence();

        @Test
        @DisplayName("passes through proposition confidence value")
        void evaluate_confidenceSet_passesThroughValue() {
            var prop = proposition("test", 0.85);

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isCloseTo(0.85, within(0.001));
        }

        @Test
        @DisplayName("zero confidence passes through as zero")
        void evaluate_zeroConfidence_returnsZero() {
            var prop = proposition("test", 0.0);

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("full confidence passes through as 1.0")
        void evaluate_fullConfidence_returnsOne() {
            var prop = proposition("test", 1.0);

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("signal name is extractionConfidence")
        void name_returnsExtractionConfidence() {
            assertThat(signal.name()).isEqualTo("extractionConfidence");
        }
    }

    // ========================================================================
    // GraphConsistencySignal
    // ========================================================================

    @Nested
    @DisplayName("GraphConsistencySignal")
    @ExtendWith(MockitoExtension.class)
    class GraphConsistencySignalTests {

        @Mock
        private ArcMemEngine arcMemEngine;

        private GraphConsistencySignal signal;

        @BeforeEach
        void setUp() {
            signal = new GraphConsistencySignal(arcMemEngine);
        }

        @Test
        @DisplayName("returns 0.5 neutral when no memory units exist (cold-start)")
        void evaluate_noUnits_returnsNeutral() {
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(List.of());
            var prop = proposition("The dragon is red", 0.8);

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isCloseTo(0.5, within(0.001));
        }

        @Test
        @DisplayName("computes Jaccard similarity with existing memory units")
        void evaluate_overlappingUnit_computesSimilarity() {
            var existingUnit = MemoryUnit.withoutTrust("unit-1", "The red dragon breathes fire",
                    500, Authority.RELIABLE, false, 0.9, 0);
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(List.of(existingUnit));
            var prop = proposition("The dragon is red", 0.8);
            prop.setId("prop-1");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            // "the dragon is red" -> {the, dragon, is, red}
            // "the red dragon breathes fire" -> {the, red, dragon, breathes, fire}
            // intersection = {the, red, dragon} = 3
            // union = 4 + 5 - 3 = 6
            // Jaccard = 3/6 = 0.5
            assertThat(result.getAsDouble()).isCloseTo(0.5, within(0.001));
        }

        @Test
        @DisplayName("skips self-comparison when memory unit ID matches proposition ID")
        void evaluate_selfUnit_skipsSelf() {
            var selfUnit = MemoryUnit.withoutTrust("self-id", "The dragon is red",
                    500, Authority.RELIABLE, false, 0.9, 0);
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(List.of(selfUnit));
            var prop = proposition("The dragon is red", 0.8);
            prop.setId("self-id");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            // Only unit is self, so maxSimilarity stays 0.0
            assertThat(result.getAsDouble()).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("returns max similarity across multiple memory units")
        void evaluate_multipleUnits_returnsMaxSimilarity() {
            var unit1 = MemoryUnit.withoutTrust("a1", "Completely unrelated text about weather",
                    500, Authority.RELIABLE, false, 0.9, 0);
            var unit2 = MemoryUnit.withoutTrust("a2", "The red dragon is fierce",
                    500, Authority.RELIABLE, false, 0.9, 0);
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(List.of(unit1, unit2));
            var prop = proposition("The red dragon is powerful", 0.8);
            prop.setId("prop-1");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            // unit2 has more overlap than unit1, so max should be from unit2
            assertThat(result.getAsDouble()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("signal name is graphConsistency")
        void name_returnsGraphConsistency() {
            assertThat(signal.name()).isEqualTo("graphConsistency");
        }
    }

    // ========================================================================
    // CorroborationSignal
    // ========================================================================

    @Nested
    @DisplayName("CorroborationSignal")
    class CorroborationSignalTests {

        private final TrustSignal signal = TrustSignal.corroboration();

        @Test
        @DisplayName("single source scores 0.3")
        void evaluate_singleSource_scores03() {
            var prop = propositionWithSources("test", 0.8, List.of("dm-1"));

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isCloseTo(0.3, within(0.001));
        }

        @Test
        @DisplayName("two distinct sources score 0.6")
        void evaluate_twoSources_scores06() {
            var prop = propositionWithSources("test", 0.8, List.of("dm-1", "player-1"));

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isCloseTo(0.6, within(0.001));
        }

        @Test
        @DisplayName("three or more distinct sources score 0.9")
        void evaluate_threeSources_scores09() {
            var prop = propositionWithSources("test", 0.8, List.of("dm-1", "player-1", "system-1"));

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isCloseTo(0.9, within(0.001));
        }

        @Test
        @DisplayName("duplicate sources count as one distinct source")
        void evaluate_duplicateSources_countsDistinct() {
            var prop = propositionWithSources("test", 0.8, List.of("dm-1", "dm-1", "dm-1"));

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isCloseTo(0.3, within(0.001));
        }

        @Test
        @DisplayName("null sourceIds returns empty (signal not applicable)")
        void evaluate_nullSourceIds_returnsEmpty() {
            var prop = proposition("test", 0.8);
            prop.setSourceIds(null);

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("empty sourceIds returns empty (signal not applicable)")
        void evaluate_emptySourceIds_returnsEmpty() {
            var prop = propositionWithSources("test", 0.8, List.of());

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("signal name is corroboration")
        void name_returnsCorroboration() {
            assertThat(signal.name()).isEqualTo("corroboration");
        }
    }

    // ========================================================================
    // DomainProfile
    // ========================================================================

    @Nested
    @DisplayName("DomainProfile")
    class DomainProfileTests {

        @Test
        @DisplayName("byName returns SECURE for 'SECURE'")
        void byName_secure_returnsSecureProfile() {
            assertThat(DomainProfile.byName("SECURE")).isSameAs(DomainProfile.SECURE);
        }

        @Test
        @DisplayName("byName returns BALANCED for 'BALANCED'")
        void byName_balanced_returnsBalancedProfile() {
            assertThat(DomainProfile.byName("BALANCED")).isSameAs(DomainProfile.BALANCED);
        }

        @Test
        @DisplayName("byName is case-insensitive")
        void byName_lowercase_returnsCaseInsensitiveMatch() {
            assertThat(DomainProfile.byName("secure")).isSameAs(DomainProfile.SECURE);
            assertThat(DomainProfile.byName("Balanced")).isSameAs(DomainProfile.BALANCED);
        }

        @Test
        @DisplayName("byName resolves NARRATIVE profile")
        void byName_narrative_returnsNarrativeProfile() {
            assertThat(DomainProfile.byName("NARRATIVE")).isSameAs(DomainProfile.NARRATIVE);
        }

        @Test
        @DisplayName("unknown name returns BALANCED as default")
        void byName_unknownName_returnsBalanced() {
            assertThat(DomainProfile.byName("UNKNOWN")).isSameAs(DomainProfile.BALANCED);
            assertThat(DomainProfile.byName("foo")).isSameAs(DomainProfile.BALANCED);
        }

        @Test
        @DisplayName("null name returns BALANCED as default")
        void byName_null_returnsBalanced() {
            assertThat(DomainProfile.byName(null)).isSameAs(DomainProfile.BALANCED);
        }

        @Test
        @DisplayName("SECURE profile has stricter autoPromoteThreshold than BALANCED")
        void secureProfile_hasStricterThresholdThanBalanced() {
            assertThat(DomainProfile.SECURE.autoPromoteThreshold())
                    .isGreaterThan(DomainProfile.BALANCED.autoPromoteThreshold());
        }

        @Test
        @DisplayName("BALANCED weights sum to 1.0")
        void balancedProfile_weightsSumToOne() {
            double sum = DomainProfile.BALANCED.weights().values().stream().mapToDouble(d -> d).sum();
            assertThat(sum).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("SECURE weights sum to 1.0")
        void secureProfile_weightsSumToOne() {
            double sum = DomainProfile.SECURE.weights().values().stream().mapToDouble(d -> d).sum();
            assertThat(sum).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("SECURE graphConsistency weight is highest among its signals")
        void secureProfile_graphConsistencyWeightIsHighest() {
            var weights = DomainProfile.SECURE.weights();
            assertThat(weights.get("graphConsistency"))
                    .isGreaterThan(weights.get("sourceAuthority"))
                    .isGreaterThan(weights.get("extractionConfidence"));
        }
    }

    // ========================================================================
    // TrustPipeline
    // ========================================================================

    @Nested
    @DisplayName("TrustPipeline")
    class TrustPipelineTests {

        @Test
        @DisplayName("defaults to BALANCED profile")
        void constructor_defaultProfile_isBalanced() {
            var pipeline = new TrustPipeline(List.of());

            assertThat(pipeline.getActiveProfile()).isSameAs(DomainProfile.BALANCED);
        }

        @Test
        @DisplayName("evaluates proposition using composed signals")
        void evaluate_withSignals_delegatesToEvaluator() {
            // Quality signals absent — weight redistributes; BALANCED core weights 0.22,0.22,0.22,0.22 -> sum=0.88
            // Effective weights after redistribution: each 0.22/0.88 = 0.25
            var signals = List.<TrustSignal>of(
                    namedSignal("sourceAuthority", 0.8),
                    namedSignal("extractionConfidence", 0.7),
                    namedSignal("graphConsistency", 0.6),
                    namedSignal("corroboration", 0.9),
                    absentSignal("novelty"),
                    absentSignal("importance")
            );
            var pipeline = new TrustPipeline(signals);
            var prop = proposition("The castle has a moat", 0.7);

            var result = pipeline.evaluate(prop, CONTEXT_ID);

            // BALANCED (redistributed): 0.25*0.8 + 0.25*0.7 + 0.25*0.6 + 0.25*0.9 = 0.75
            assertThat(result.score()).isCloseTo(0.75, within(0.001));
            assertThat(result).isNotNull();
            assertThat(result.signalAudit()).isNotEmpty();
        }

        @Test
        @DisplayName("withProfile switches active profile")
        void withProfile_switchesProfile_returnsNewProfile() {
            var pipeline = new TrustPipeline(List.of());

            var returned = pipeline.withProfile(DomainProfile.SECURE);

            assertThat(returned).isSameAs(pipeline);
            assertThat(pipeline.getActiveProfile()).isSameAs(DomainProfile.SECURE);
        }

        @Test
        @DisplayName("evaluates with switched profile thresholds")
        void evaluate_afterProfileSwitch_usesNewProfileThresholds() {
            // Quality signals absent — redistribution preserves proportions; all core signals at 0.60 -> score = 0.60
            // 0.60 is below BALANCED autoPromote (0.65) but above a permissive profile (0.55)
            var signals = List.<TrustSignal>of(
                    namedSignal("sourceAuthority", 0.60),
                    namedSignal("extractionConfidence", 0.60),
                    namedSignal("graphConsistency", 0.60),
                    namedSignal("corroboration", 0.60),
                    absentSignal("novelty"),
                    absentSignal("importance")
            );
            var pipeline = new TrustPipeline(signals);
            var prop = proposition("test", 0.60);

            // With BALANCED default (autoPromote=0.65), score=0.60 -> REVIEW
            var balancedResult = pipeline.evaluate(prop, CONTEXT_ID);
            assertThat(balancedResult.promotionZone()).isEqualTo(PromotionZone.REVIEW);

            // Switch to permissive profile (autoPromote=0.55), score=0.60 -> AUTO_PROMOTE
            var permissive = new DomainProfile("PERMISSIVE",
                    Map.of("sourceAuthority", 0.25, "extractionConfidence", 0.25,
                           "graphConsistency", 0.25, "corroboration", 0.25),
                    0.55, 0.35, 0.20);
            pipeline.withProfile(permissive);
            var permissiveResult = pipeline.evaluate(prop, CONTEXT_ID);
            assertThat(permissiveResult.promotionZone()).isEqualTo(PromotionZone.AUTO_PROMOTE);
        }

        @Test
        @DisplayName("handles absent signals through evaluator redistribution")
        void evaluate_absentSignal_redistributesViaEvaluator() {
            // corroboration, novelty, importance all absent — 3 core signals present
            // BALANCED: present weight = 0.22+0.22+0.22 = 0.66, absent = 0.22+0.06+0.06 = 0.34
            // All present signals at 0.8 -> redistribution preserves 0.8 as score
            var signals = List.<TrustSignal>of(
                    namedSignal("sourceAuthority", 0.8),
                    namedSignal("extractionConfidence", 0.8),
                    namedSignal("graphConsistency", 0.8),
                    absentSignal("corroboration"),
                    absentSignal("novelty"),
                    absentSignal("importance")
            );
            var pipeline = new TrustPipeline(signals);
            var prop = proposition("test", 0.8);

            var result = pipeline.evaluate(prop, CONTEXT_ID);

            // All present signals at same value 0.8 -> redistribution preserves that value as score
            assertThat(result.score()).isCloseTo(0.8, within(0.001));
        }
    }

    // ========================================================================
    // Test helpers
    // ========================================================================

    private static TrustSignal namedSignal(String name, double value) {
        return new TrustSignal() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public OptionalDouble evaluate(PropositionNode proposition, String contextId) {
                return OptionalDouble.of(value);
            }
        };
    }

    private static TrustSignal absentSignal(String name) {
        return new TrustSignal() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public OptionalDouble evaluate(PropositionNode proposition, String contextId) {
                return OptionalDouble.empty();
            }
        };
    }
}
