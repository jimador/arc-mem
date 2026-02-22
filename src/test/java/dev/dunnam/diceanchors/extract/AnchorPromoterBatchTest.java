package dev.dunnam.diceanchors.extract;

import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionStatus;
import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.ConflictDetector;
import dev.dunnam.diceanchors.anchor.PromotionZone;
import dev.dunnam.diceanchors.anchor.TrustPipeline;
import dev.dunnam.diceanchors.anchor.TrustScore;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnchorPromoter batch operations")
class AnchorPromoterBatchTest {

    private static final String CONTEXT_ID = "test-ctx";
    private static final double THRESHOLD = 0.65;
    private static final int INITIAL_RANK = 500;
    private static final int BATCH_MAX_SIZE = 10;

    @Mock private AnchorEngine engine;
    @Mock private TrustPipeline trustPipeline;
    @Mock private AnchorRepository repository;
    @Mock private DuplicateDetector duplicateDetector;

    private AnchorPromoter promoter;

    @BeforeEach
    void setUp() {
        var anchorConfig = new DiceAnchorsProperties.AnchorConfig(
                20, INITIAL_RANK, 100, 900, true, THRESHOLD, "FAST_THEN_LLM", "TIERED", true, true, true, 0.6, 400, 200, null);
        var simConfig = new DiceAnchorsProperties.SimConfig(
                "gpt-4.1-mini", 30, 30, BATCH_MAX_SIZE, true);
        var properties = new DiceAnchorsProperties(
                anchorConfig, null, null, null, simConfig, null, null,
                new DiceAnchorsProperties.AssemblyConfig(0));
        promoter = new AnchorPromoter(engine, properties, trustPipeline, repository, duplicateDetector);
    }

    private Proposition activeProposition(String id, String text, double confidence) {
        var prop = mock(Proposition.class);
        // Use lenient stubs since not all tests will reach getId/getText
        lenient().when(prop.getId()).thenReturn(id);
        lenient().when(prop.getText()).thenReturn(text);
        when(prop.getConfidence()).thenReturn(confidence);
        when(prop.getStatus()).thenReturn(PropositionStatus.ACTIVE);
        return prop;
    }

    private TrustScore autoPromoteScore() {
        return new TrustScore(0.9, Authority.RELIABLE, PromotionZone.AUTO_PROMOTE,
                Map.of(), Instant.now());
    }

    private TrustScore reviewScore() {
        return new TrustScore(0.5, Authority.UNRELIABLE, PromotionZone.REVIEW,
                Map.of(), Instant.now());
    }

    @Nested
    @DisplayName("Cross-reference dedup against existing anchors")
    class CrossReferenceDedup {

        @Test
        @DisplayName("candidate matching existing anchor text is filtered")
        void candidateMatchingExistingAnchorTextIsFiltered() {
            var prop = activeProposition("p1", "The dragon is red", 0.9);
            var existingAnchor = Anchor.withoutTrust("a1", "The dragon is red", 500, Authority.RELIABLE, false, 0.9, 0);
            when(engine.findByContext(CONTEXT_ID)).thenReturn(List.of(existingAnchor));

            var result = promoter.batchEvaluateAndPromote(CONTEXT_ID, List.of(prop));

            assertThat(result).isZero();
            verify(engine, never()).promote(anyString(), anyInt());
        }

        @Test
        @DisplayName("unique candidate passes through existing-anchor dedup gate")
        void uniqueCandidatePassesThroughExistingAnchorDedupGate() {
            var prop = activeProposition("p1", "The dragon breathes fire", 0.9);
            var existingAnchor = Anchor.withoutTrust("a1", "The dragon is red", 500, Authority.RELIABLE, false, 0.9, 0);
            when(engine.findByContext(CONTEXT_ID)).thenReturn(List.of(existingAnchor));

            when(duplicateDetector.batchIsDuplicate(eq(CONTEXT_ID), anyList()))
                    .thenReturn(Map.of("The dragon breathes fire", false));
            when(engine.batchDetectConflicts(eq(CONTEXT_ID), anyList()))
                    .thenReturn(Map.of("The dragon breathes fire", List.of()));
            var node = mock(PropositionNode.class);
            lenient().when(node.getText()).thenReturn("The dragon breathes fire");
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.of(node));
            when(trustPipeline.batchEvaluate(anyList()))
                    .thenReturn(Map.of("The dragon breathes fire", autoPromoteScore()));

            var result = promoter.batchEvaluateAndPromote(CONTEXT_ID, List.of(prop));

            assertThat(result).isEqualTo(1);
            verify(engine).promote("p1", INITIAL_RANK, Authority.RELIABLE);
        }
    }

    @Nested
    @DisplayName("all filtered at confidence gate")
    class ConfidenceGate {

        @Test
        @DisplayName("returns zero with no LLM calls when all below threshold")
        void batchEvaluateAndPromoteAllFilteredAtConfidenceNoLlmCalls() {
            var prop1 = activeProposition("p1", "Low fact 1", 0.3);
            var prop2 = activeProposition("p2", "Low fact 2", 0.4);

            var result = promoter.batchEvaluateAndPromote(CONTEXT_ID, List.of(prop1, prop2));

            assertThat(result).isZero();
            verify(duplicateDetector, never()).batchIsDuplicate(anyString(), anyList());
            verify(engine, never()).batchDetectConflicts(anyString(), anyList());
            verify(engine, never()).promote(anyString(), anyInt());
        }

        @Test
        @DisplayName("empty proposition list returns zero")
        void batchEvaluateAndPromoteEmptyListReturnsZero() {
            var result = promoter.batchEvaluateAndPromote(CONTEXT_ID, List.of());

            assertThat(result).isZero();
            verify(duplicateDetector, never()).batchIsDuplicate(anyString(), anyList());
        }
    }

    @Nested
    @DisplayName("full pipeline promotion")
    class FullPipeline {

        @Test
        @DisplayName("promotes propositions passing all gates")
        void batchEvaluateAndPromoteFullPipelinePromotion() {
            var prop = activeProposition("p1", "Valid high-confidence fact", 0.9);

            when(duplicateDetector.batchIsDuplicate(eq(CONTEXT_ID), anyList()))
                    .thenReturn(Map.of("Valid high-confidence fact", false));
            when(engine.batchDetectConflicts(eq(CONTEXT_ID), anyList()))
                    .thenReturn(Map.of("Valid high-confidence fact", List.of()));
            var node = mock(PropositionNode.class);
            lenient().when(node.getText()).thenReturn("Valid high-confidence fact");
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.of(node));
            when(trustPipeline.batchEvaluate(anyList()))
                    .thenReturn(Map.of("Valid high-confidence fact", autoPromoteScore()));

            var result = promoter.batchEvaluateAndPromote(CONTEXT_ID, List.of(prop));

            assertThat(result).isEqualTo(1);
            verify(engine).promote("p1", INITIAL_RANK, Authority.RELIABLE);
        }

        @Test
        @DisplayName("filters duplicates at dedup gate")
        void batchEvaluateAndPromoteFiltersDuplicates() {
            var prop = activeProposition("p1", "Duplicate fact", 0.9);

            when(duplicateDetector.batchIsDuplicate(eq(CONTEXT_ID), anyList()))
                    .thenReturn(Map.of("Duplicate fact", true));

            var result = promoter.batchEvaluateAndPromote(CONTEXT_ID, List.of(prop));

            assertThat(result).isZero();
            verify(engine, never()).batchDetectConflicts(anyString(), anyList());
            verify(engine, never()).promote(anyString(), anyInt());
        }

        @Test
        @DisplayName("filters propositions in REVIEW trust zone")
        void batchEvaluateAndPromoteFiltersReviewZone() {
            var prop = activeProposition("p1", "Review fact", 0.9);

            when(duplicateDetector.batchIsDuplicate(eq(CONTEXT_ID), anyList()))
                    .thenReturn(Map.of("Review fact", false));
            when(engine.batchDetectConflicts(eq(CONTEXT_ID), anyList()))
                    .thenReturn(Map.of("Review fact", List.of()));
            var node = mock(PropositionNode.class);
            lenient().when(node.getText()).thenReturn("Review fact");
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.of(node));
            when(trustPipeline.batchEvaluate(anyList()))
                    .thenReturn(Map.of("Review fact", reviewScore()));

            var result = promoter.batchEvaluateAndPromote(CONTEXT_ID, List.of(prop));

            assertThat(result).isZero();
            verify(engine, never()).promote(anyString(), anyInt());
        }

        @Test
        @DisplayName("promotes multiple propositions when all pass gates")
        void batchEvaluateAndPromoteMultiplePropositions() {
            var prop1 = activeProposition("p1", "Fact one", 0.9);
            var prop2 = activeProposition("p2", "Fact two", 0.9);

            when(duplicateDetector.batchIsDuplicate(eq(CONTEXT_ID), anyList()))
                    .thenReturn(Map.of("Fact one", false, "Fact two", false));
            when(engine.batchDetectConflicts(eq(CONTEXT_ID), anyList()))
                    .thenReturn(Map.of("Fact one", List.of(), "Fact two", List.of()));
            var node1 = mock(PropositionNode.class);
            lenient().when(node1.getText()).thenReturn("Fact one");
            var node2 = mock(PropositionNode.class);
            lenient().when(node2.getText()).thenReturn("Fact two");
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.of(node1));
            when(repository.findPropositionNodeById("p2")).thenReturn(Optional.of(node2));
            when(trustPipeline.batchEvaluate(anyList()))
                    .thenReturn(Map.of("Fact one", autoPromoteScore(), "Fact two", autoPromoteScore()));

            var result = promoter.batchEvaluateAndPromote(CONTEXT_ID, List.of(prop1, prop2));

            assertThat(result).isEqualTo(2);
            verify(engine).promote("p1", INITIAL_RANK, Authority.RELIABLE);
            verify(engine).promote("p2", INITIAL_RANK, Authority.RELIABLE);
        }

        @Test
        @DisplayName("duplicate-text propositions in batch do not cause IllegalStateException")
        void batchEvaluateAndPromoteDuplicateTextPropositionsHandledGracefully() {
            // DICE can extract the same text twice in one response — both pass confidence
            // and dedup-against-existing gates, but intra-batch dedup must prevent the crash
            // in TrustPipeline.batchEvaluate()'s Collectors.toMap().
            var prop1 = activeProposition("p1", "The temple demands a blood tithe", 0.9);
            var prop2 = activeProposition("p2", "The temple demands a blood tithe", 0.9); // same text

            var node = new PropositionNode(
                    "p1", CONTEXT_ID, "The temple demands a blood tithe",
                    0.9, 0.0, null, List.of(),
                    java.time.Instant.now(), java.time.Instant.now(),
                    com.embabel.dice.proposition.PropositionStatus.ACTIVE,
                    null, List.of());

            when(duplicateDetector.batchIsDuplicate(eq(CONTEXT_ID), anyList()))
                    .thenReturn(Map.of("The temple demands a blood tithe", false));
            when(engine.batchDetectConflicts(eq(CONTEXT_ID), anyList()))
                    .thenReturn(Map.of("The temple demands a blood tithe", List.of()));
            when(repository.findPropositionNodeById(anyString())).thenReturn(Optional.of(node));
            when(trustPipeline.batchEvaluate(anyList())).thenReturn(
                    Map.of("The temple demands a blood tithe", autoPromoteScore()));

            // Must not throw IllegalStateException: Duplicate key
            var result = promoter.batchEvaluateAndPromote(CONTEXT_ID, List.of(prop1, prop2));

            // Only one should be promoted (first wins, duplicate discarded)
            assertThat(result).isEqualTo(1);
        }

        @Test
        @DisplayName("proposition with no node found still allowed through trust gate")
        void batchEvaluateAndPromoteNoNodeAllowed() {
            var prop = activeProposition("p1", "Fact without node", 0.9);

            when(duplicateDetector.batchIsDuplicate(eq(CONTEXT_ID), anyList()))
                    .thenReturn(Map.of("Fact without node", false));
            when(engine.batchDetectConflicts(eq(CONTEXT_ID), anyList()))
                    .thenReturn(Map.of("Fact without node", List.of()));
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.empty());
            // node is null so no TrustContext added — batchEvaluate returns empty map
            when(trustPipeline.batchEvaluate(anyList())).thenReturn(Map.of());

            var result = promoter.batchEvaluateAndPromote(CONTEXT_ID, List.of(prop));

            // score == null means allow (matches sequential behavior)
            assertThat(result).isEqualTo(1);
            verify(engine).promote("p1", INITIAL_RANK);
        }
    }
}
