package dev.dunnam.diceanchors.extract;

import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionStatus;
import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.*;
import dev.dunnam.diceanchors.anchor.event.ArchiveReason;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.persistence.PropositionNode;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnchorPromoter")
class AnchorPromoterTest {

    private static final String CONTEXT_ID = "test-ctx";
    private static final double THRESHOLD = 0.65;
    private static final int INITIAL_RANK = 500;

    @Mock private AnchorEngine engine;
    @Mock private TrustPipeline trustPipeline;
    @Mock private AnchorRepository repository;
    @Mock private DuplicateDetector duplicateDetector;

    private AnchorPromoter promoter;

    @BeforeEach
    void setUp() {
        var anchorConfig = new DiceAnchorsProperties.AnchorConfig(20, INITIAL_RANK, 100, 900, true, THRESHOLD, "FAST_THEN_LLM", "TIERED", true, true, true, 0.6, 400, 200);
        var properties = new DiceAnchorsProperties(
                anchorConfig, null, null, null, null, null, null, new DiceAnchorsProperties.AssemblyConfig(0)
        );
        promoter = new AnchorPromoter(engine, properties, trustPipeline, repository, duplicateDetector);
    }

    private Proposition activeProposition(String id, String text, double confidence) {
        var prop = mock(Proposition.class);
        lenient().when(prop.getId()).thenReturn(id);
        lenient().when(prop.getText()).thenReturn(text);
        when(prop.getConfidence()).thenReturn(confidence);
        when(prop.getStatus()).thenReturn(PropositionStatus.ACTIVE);
        return prop;
    }

    private PropositionNode mockNode() {
        return mock(PropositionNode.class);
    }

    private TrustScore autoPromoteScore() {
        return new TrustScore(0.8, Authority.RELIABLE, PromotionZone.AUTO_PROMOTE,
                              Map.of(), Instant.now());
    }

    private void allowPromotion(String propId) {
        when(duplicateDetector.isDuplicate(eq(CONTEXT_ID), anyString())).thenReturn(false);
        when(engine.detectConflicts(eq(CONTEXT_ID), anyString())).thenReturn(List.of());
        var node = mockNode();
        when(repository.findPropositionNodeById(propId)).thenReturn(Optional.of(node));
        when(trustPipeline.evaluate(eq(node), eq(CONTEXT_ID))).thenReturn(autoPromoteScore());
    }

    @Nested
    @DisplayName("confidence gate")
    class ConfidenceGate {

        @Test
        @DisplayName("skips proposition below confidence threshold")
        void belowThresholdSkipped() {
            var prop = activeProposition("p1", "Low confidence fact", 0.3);

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(prop));

            assertThat(result).isZero();
            verify(duplicateDetector, never()).isDuplicate(anyString(), anyString());
        }

        @Test
        @DisplayName("passes proposition at threshold")
        void atThresholdPasses() {
            var prop = activeProposition("p1", "At threshold fact", THRESHOLD);
            allowPromotion("p1");

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(prop));

            assertThat(result).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("duplicate detection gate")
    class DuplicateDetectionGate {

        @Test
        @DisplayName("rejects duplicate proposition")
        void duplicateRejected() {
            var prop = activeProposition("p1", "Duplicate fact", 0.9);
            when(duplicateDetector.isDuplicate(CONTEXT_ID, "Duplicate fact")).thenReturn(true);

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(prop));

            assertThat(result).isZero();
            verify(engine, never()).detectConflicts(anyString(), anyString());
        }

        @Test
        @DisplayName("passes novel proposition to conflict detection")
        void novelPassesToConflict() {
            var prop = activeProposition("p1", "Novel fact", 0.9);
            allowPromotion("p1");

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(prop));

            assertThat(result).isEqualTo(1);
            verify(engine).detectConflicts(CONTEXT_ID, "Novel fact");
        }
    }

    @Nested
    @DisplayName("conflict resolution gate")
    class ConflictResolutionGate {

        private ConflictDetector.Conflict makeConflict(String anchorId, String anchorText) {
            var existingAnchor = Anchor.withoutTrust(
                    anchorId, anchorText, 700, Authority.PROVISIONAL, false, 0.8, 0
            );
            return new ConflictDetector.Conflict(existingAnchor, "incoming text", 0.9, "negation");
        }

        @Test
        @DisplayName("KEEP_EXISTING rejects incoming proposition")
        void keepExistingRejectsIncoming() {
            var prop = activeProposition("p1", "incoming text", 0.9);
            when(duplicateDetector.isDuplicate(CONTEXT_ID, "incoming text")).thenReturn(false);
            var conflict = makeConflict("a1", "existing text");
            when(engine.detectConflicts(CONTEXT_ID, "incoming text")).thenReturn(List.of(conflict));
            when(engine.resolveConflict(conflict)).thenReturn(ConflictResolver.Resolution.KEEP_EXISTING);

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(prop));

            assertThat(result).isZero();
            verify(engine, never()).promote(anyString(), anyInt());
        }

        @Test
        @DisplayName("REPLACE archives existing anchor and promotes incoming")
        void replaceArchivesExistingAndPromotes() {
            var prop = activeProposition("p1", "incoming text", 0.9);
            when(duplicateDetector.isDuplicate(CONTEXT_ID, "incoming text")).thenReturn(false);
            var conflict = makeConflict("a1", "existing text");
            when(engine.detectConflicts(CONTEXT_ID, "incoming text")).thenReturn(List.of(conflict));
            when(engine.resolveConflict(conflict)).thenReturn(ConflictResolver.Resolution.REPLACE);
            var node = mockNode();
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.of(node));
            when(trustPipeline.evaluate(eq(node), eq(CONTEXT_ID))).thenReturn(autoPromoteScore());

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(prop));

            assertThat(result).isEqualTo(1);
            verify(engine).archive("a1", ArchiveReason.CONFLICT_REPLACEMENT);
            verify(engine).promote("p1", INITIAL_RANK, Authority.RELIABLE);
        }

        @Test
        @DisplayName("COEXIST allows promotion to proceed")
        void coexistAllowsPromotion() {
            var prop = activeProposition("p1", "incoming text", 0.9);
            when(duplicateDetector.isDuplicate(CONTEXT_ID, "incoming text")).thenReturn(false);
            var conflict = makeConflict("a1", "existing text");
            when(engine.detectConflicts(CONTEXT_ID, "incoming text")).thenReturn(List.of(conflict));
            when(engine.resolveConflict(conflict)).thenReturn(ConflictResolver.Resolution.COEXIST);
            var node = mockNode();
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.of(node));
            when(trustPipeline.evaluate(eq(node), eq(CONTEXT_ID))).thenReturn(autoPromoteScore());

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(prop));

            assertThat(result).isEqualTo(1);
            verify(engine, never()).archive(anyString(), any());
        }

        @Test
        @DisplayName("KEEP_EXISTING takes precedence over COEXIST in multiple conflicts")
        void keepTakesPrecedenceOverCoexist() {
            var prop = activeProposition("p1", "incoming text", 0.9);
            when(duplicateDetector.isDuplicate(CONTEXT_ID, "incoming text")).thenReturn(false);
            var conflict1 = makeConflict("a1", "anchor 1");
            var conflict2 = makeConflict("a2", "anchor 2");
            when(engine.detectConflicts(CONTEXT_ID, "incoming text"))
                    .thenReturn(List.of(conflict1, conflict2));
            when(engine.resolveConflict(conflict1)).thenReturn(ConflictResolver.Resolution.COEXIST);
            when(engine.resolveConflict(conflict2)).thenReturn(ConflictResolver.Resolution.KEEP_EXISTING);

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(prop));

            assertThat(result).isZero();
        }
    }

    @Nested
    @DisplayName("pipeline gate ordering")
    class GateOrdering {

        @Test
        @DisplayName("dedup runs before conflict detection")
        void dedupBeforeConflict() {
            var prop = activeProposition("p1", "text", 0.9);
            when(duplicateDetector.isDuplicate(CONTEXT_ID, "text")).thenReturn(true);

            promoter.evaluateAndPromote(CONTEXT_ID, List.of(prop));

            verify(engine, never()).detectConflicts(anyString(), anyString());
        }

        @Test
        @DisplayName("conflict runs before trust evaluation")
        void conflictBeforeTrust() {
            var prop = activeProposition("p1", "text", 0.9);
            when(duplicateDetector.isDuplicate(CONTEXT_ID, "text")).thenReturn(false);
            var conflict = new ConflictDetector.Conflict(
                    Anchor.withoutTrust("a1", "old", 700, Authority.RELIABLE, false, 0.9, 0),
                    "text", 0.9, "negation"
            );
            when(engine.detectConflicts(CONTEXT_ID, "text")).thenReturn(List.of(conflict));
            when(engine.resolveConflict(conflict)).thenReturn(ConflictResolver.Resolution.KEEP_EXISTING);

            promoter.evaluateAndPromote(CONTEXT_ID, List.of(prop));

            verify(repository, never()).findPropositionNodeById(anyString());
            verify(trustPipeline, never()).evaluate(any(), anyString());
        }
    }

    @Nested
    @DisplayName("trust ceiling enforcement")
    class TrustCeilingEnforcement {

        @Test
        @DisplayName("ceiling=PROVISIONAL limits initial authority passed to engine.promote()")
        void ceilingLimitsInitialAuthority() {
            var prop = activeProposition("p1", "Valid fact", 0.9);
            when(duplicateDetector.isDuplicate(CONTEXT_ID, "Valid fact")).thenReturn(false);
            when(engine.detectConflicts(CONTEXT_ID, "Valid fact")).thenReturn(List.of());
            var node = mockNode();
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.of(node));
            var ceiledScore = new TrustScore(0.8, Authority.PROVISIONAL, PromotionZone.AUTO_PROMOTE,
                    Map.of(), java.time.Instant.now());
            when(trustPipeline.evaluate(eq(node), eq(CONTEXT_ID))).thenReturn(ceiledScore);

            promoter.evaluateAndPromote(CONTEXT_ID, List.of(prop));

            verify(engine).promote("p1", INITIAL_RANK, Authority.PROVISIONAL);
        }

        @Test
        @DisplayName("ceiling=RELIABLE passes RELIABLE authority ceiling to engine.promote()")
        void ceilingAboveAssignedHasNoEffect() {
            var prop = activeProposition("p1", "Valid fact", 0.9);
            when(duplicateDetector.isDuplicate(CONTEXT_ID, "Valid fact")).thenReturn(false);
            when(engine.detectConflicts(CONTEXT_ID, "Valid fact")).thenReturn(List.of());
            var node = mockNode();
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.of(node));
            var reliableScore = new TrustScore(0.8, Authority.RELIABLE, PromotionZone.AUTO_PROMOTE,
                    Map.of(), java.time.Instant.now());
            when(trustPipeline.evaluate(eq(node), eq(CONTEXT_ID))).thenReturn(reliableScore);

            promoter.evaluateAndPromote(CONTEXT_ID, List.of(prop));

            verify(engine).promote("p1", INITIAL_RANK, Authority.RELIABLE);
        }

        @Test
        @DisplayName("no trust score (node not found) uses two-arg promote()")
        void noTrustScoreUsesTwoArgPromote() {
            var prop = activeProposition("p1", "Valid fact", 0.9);
            when(duplicateDetector.isDuplicate(CONTEXT_ID, "Valid fact")).thenReturn(false);
            when(engine.detectConflicts(CONTEXT_ID, "Valid fact")).thenReturn(List.of());
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.empty());

            promoter.evaluateAndPromote(CONTEXT_ID, List.of(prop));

            verify(engine).promote("p1", INITIAL_RANK);
            verify(engine, never()).promote(eq("p1"), anyInt(), any());
        }
    }

    @Nested
    @DisplayName("Trust re-evaluation triggers")
    class TrustReEvaluationTriggers {

        @Test
        @DisplayName("KEEP_EXISTING resolution triggers trust re-evaluation on kept anchor")
        void keepExistingResolutionTriggersTrustReEvaluation() {
            var prop = activeProposition("p1", "incoming text", 0.9);
            when(duplicateDetector.isDuplicate(CONTEXT_ID, "incoming text")).thenReturn(false);
            var existingAnchor = Anchor.withoutTrust("a1", "existing text", 700, Authority.PROVISIONAL, false, 0.8, 0);
            var conflict = new ConflictDetector.Conflict(existingAnchor, "incoming text", 0.9, "negation");
            when(engine.detectConflicts(CONTEXT_ID, "incoming text")).thenReturn(List.of(conflict));
            when(engine.resolveConflict(conflict)).thenReturn(ConflictResolver.Resolution.KEEP_EXISTING);

            promoter.evaluateAndPromote(CONTEXT_ID, List.of(prop));

            verify(engine).reEvaluateTrust("a1");
        }
    }

    @Nested
    @DisplayName("full pipeline")
    class FullPipeline {

        @Test
        @DisplayName("promotes proposition passing all gates")
        void fullPipelinePromotes() {
            var prop = activeProposition("p1", "Valid fact", 0.9);
            allowPromotion("p1");

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(prop));

            assertThat(result).isEqualTo(1);
            verify(engine).promote("p1", INITIAL_RANK, Authority.RELIABLE);
        }

        @Test
        @DisplayName("skips non-ACTIVE propositions")
        void nonActiveSkipped() {
            var prop = mock(Proposition.class);
            when(prop.getStatus()).thenReturn(null);

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(prop));

            assertThat(result).isZero();
        }

        @Test
        @DisplayName("allows PROMOTED propositions through the pipeline")
        void promotedStatusAllowed() {
            var prop = activeProposition("p1", "already revised fact", 0.9);
            when(prop.getStatus()).thenReturn(PropositionStatus.PROMOTED);
            allowPromotion("p1");

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(prop));

            assertThat(result).isEqualTo(1);
            verify(engine).promote("p1", INITIAL_RANK, Authority.RELIABLE);
        }

        @Test
        @DisplayName("processes multiple propositions independently")
        void multiplePropositionsIndependent() {
            var good = activeProposition("p1", "Good fact", 0.9);
            var dup = activeProposition("p2", "Dup fact", 0.9);

            when(duplicateDetector.isDuplicate(CONTEXT_ID, "Good fact")).thenReturn(false);
            when(duplicateDetector.isDuplicate(CONTEXT_ID, "Dup fact")).thenReturn(true);
            when(engine.detectConflicts(CONTEXT_ID, "Good fact")).thenReturn(List.of());
            var node = mockNode();
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.of(node));
            when(trustPipeline.evaluate(eq(node), eq(CONTEXT_ID))).thenReturn(autoPromoteScore());

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(good, dup));

            assertThat(result).isEqualTo(1);
            verify(engine).promote("p1", INITIAL_RANK, Authority.RELIABLE);
            verify(engine, never()).promote(eq("p2"), anyInt(), any());
        }
    }
}
