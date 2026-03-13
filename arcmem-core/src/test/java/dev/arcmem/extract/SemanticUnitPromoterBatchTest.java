package dev.arcmem.core.extraction;
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

import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionStatus;
import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.core.persistence.MemoryUnitRepository;
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
@DisplayName("SemanticUnitPromoter batch operations")
class SemanticUnitPromoterBatchTest {

    private static final String CONTEXT_ID = "test-ctx";
    private static final double THRESHOLD = 0.65;
    private static final int INITIAL_RANK = 500;
    private static final int BATCH_MAX_SIZE = 10;

    @Mock private ArcMemEngine engine;
    @Mock private TrustPipeline trustPipeline;
    @Mock private MemoryUnitRepository repository;
    @Mock private DuplicateDetector duplicateDetector;

    private SemanticUnitPromoter promoter;

    @BeforeEach
    void setUp() {
        var unitConfig = new ArcMemProperties.UnitConfig(
                20, INITIAL_RANK, 100, 900, true, THRESHOLD, DedupStrategy.FAST_THEN_LLM, CompliancePolicyMode.TIERED, true, true, true, 0.6, 400, 200, null, null, null, null, null);
        var properties = new ArcMemProperties(
                unitConfig, null, null, null,
                new ArcMemProperties.AssemblyConfig(0, false, dev.arcmem.core.assembly.compliance.EnforcementStrategy.PROMPT_ONLY), null, null, null, null, null, null, null, new ArcMemProperties.LlmCallConfig(30, BATCH_MAX_SIZE));
        promoter = new SemanticUnitPromoter(engine, properties, trustPipeline, repository, duplicateDetector,
                Optional.empty());
    }

    private SemanticUnit activeUnit(String id, String text, double confidence) {
        return new SemanticUnit() {
            @Override public String id() { return id; }
            @Override public String text() { return text; }
            @Override public double confidence() { return confidence; }
            @Override public boolean isPromotionCandidate() { return true; }
        };
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
    @DisplayName("Cross-reference dedup against existing memory units")
    class CrossReferenceDedup {

        @Test
        @DisplayName("candidate matching existing memory unit text is filtered")
        void candidateMatchingExistingUnitTextIsFiltered() {
            var unit = activeUnit("p1", "The dragon is red", 0.9);
            var existingUnit = MemoryUnit.withoutTrust("a1", "The dragon is red", 500, Authority.RELIABLE, false, 0.9, 0);
            when(engine.findByContext(CONTEXT_ID)).thenReturn(List.of(existingUnit));

            var result = promoter.batchEvaluateAndPromote(CONTEXT_ID, List.of(unit));

            assertThat(result).isZero();
            verify(engine, never()).promote(anyString(), anyInt());
        }

        @Test
        @DisplayName("unique candidate passes through existing-context-unit dedup gate")
        void uniqueCandidatePassesThroughExistingUnitDedupGate() {
            var unit = activeUnit("p1", "The dragon breathes fire", 0.9);
            var existingUnit = MemoryUnit.withoutTrust("a1", "The dragon is red", 500, Authority.RELIABLE, false, 0.9, 0);
            when(engine.findByContext(CONTEXT_ID)).thenReturn(List.of(existingUnit));

            when(duplicateDetector.batchIsDuplicate(anyList(), anyList()))
                    .thenReturn(Map.of("The dragon breathes fire", false));
            when(engine.batchDetectConflicts(eq(CONTEXT_ID), anyList()))
                    .thenReturn(Map.of("The dragon breathes fire", List.of()));
            var node = mock(PropositionNode.class);
            lenient().when(node.getText()).thenReturn("The dragon breathes fire");
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.of(node));
            when(trustPipeline.batchEvaluate(anyList()))
                    .thenReturn(Map.of("The dragon breathes fire", autoPromoteScore()));

            var result = promoter.batchEvaluateAndPromote(CONTEXT_ID, List.of(unit));

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
            var unit1 = activeUnit("p1", "Low fact 1", 0.3);
            var unit2 = activeUnit("p2", "Low fact 2", 0.4);

            var result = promoter.batchEvaluateAndPromote(CONTEXT_ID, List.of(unit1, unit2));

            assertThat(result).isZero();
            verify(duplicateDetector, never()).batchIsDuplicate(anyList(), anyList());
            verify(engine, never()).batchDetectConflicts(anyString(), anyList());
            verify(engine, never()).promote(anyString(), anyInt());
        }

        @Test
        @DisplayName("empty list returns zero")
        void batchEvaluateAndPromoteEmptyListReturnsZero() {
            var result = promoter.batchEvaluateAndPromote(CONTEXT_ID, List.of());

            assertThat(result).isZero();
            verify(duplicateDetector, never()).batchIsDuplicate(anyList(), anyList());
        }
    }

    @Nested
    @DisplayName("full pipeline promotion")
    class FullPipeline {

        @Test
        @DisplayName("promotes units passing all gates")
        void batchEvaluateAndPromoteFullPipelinePromotion() {
            var unit = activeUnit("p1", "Valid high-confidence fact", 0.9);

            when(duplicateDetector.batchIsDuplicate(anyList(), anyList()))
                    .thenReturn(Map.of("Valid high-confidence fact", false));
            when(engine.batchDetectConflicts(eq(CONTEXT_ID), anyList()))
                    .thenReturn(Map.of("Valid high-confidence fact", List.of()));
            var node = mock(PropositionNode.class);
            lenient().when(node.getText()).thenReturn("Valid high-confidence fact");
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.of(node));
            when(trustPipeline.batchEvaluate(anyList()))
                    .thenReturn(Map.of("Valid high-confidence fact", autoPromoteScore()));

            var result = promoter.batchEvaluateAndPromote(CONTEXT_ID, List.of(unit));

            assertThat(result).isEqualTo(1);
            verify(engine).promote("p1", INITIAL_RANK, Authority.RELIABLE);
        }

        @Test
        @DisplayName("filters duplicates at dedup gate")
        void batchEvaluateAndPromoteFiltersDuplicates() {
            var unit = activeUnit("p1", "Duplicate fact", 0.9);

            when(duplicateDetector.batchIsDuplicate(anyList(), anyList()))
                    .thenReturn(Map.of("Duplicate fact", true));

            var result = promoter.batchEvaluateAndPromote(CONTEXT_ID, List.of(unit));

            assertThat(result).isZero();
            verify(engine, never()).batchDetectConflicts(anyString(), anyList());
            verify(engine, never()).promote(anyString(), anyInt());
        }

        @Test
        @DisplayName("filters units in REVIEW trust zone")
        void batchEvaluateAndPromoteFiltersReviewZone() {
            var unit = activeUnit("p1", "Review fact", 0.9);

            when(duplicateDetector.batchIsDuplicate(anyList(), anyList()))
                    .thenReturn(Map.of("Review fact", false));
            when(engine.batchDetectConflicts(eq(CONTEXT_ID), anyList()))
                    .thenReturn(Map.of("Review fact", List.of()));
            var node = mock(PropositionNode.class);
            lenient().when(node.getText()).thenReturn("Review fact");
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.of(node));
            when(trustPipeline.batchEvaluate(anyList()))
                    .thenReturn(Map.of("Review fact", reviewScore()));

            var result = promoter.batchEvaluateAndPromote(CONTEXT_ID, List.of(unit));

            assertThat(result).isZero();
            verify(engine, never()).promote(anyString(), anyInt());
        }

        @Test
        @DisplayName("promotes multiple units when all pass gates")
        void batchEvaluateAndPromoteMultipleUnits() {
            var unit1 = activeUnit("p1", "Fact one", 0.9);
            var unit2 = activeUnit("p2", "Fact two", 0.9);

            when(duplicateDetector.batchIsDuplicate(anyList(), anyList()))
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

            var result = promoter.batchEvaluateAndPromote(CONTEXT_ID, List.of(unit1, unit2));

            assertThat(result).isEqualTo(2);
            verify(engine).promote("p1", INITIAL_RANK, Authority.RELIABLE);
            verify(engine).promote("p2", INITIAL_RANK, Authority.RELIABLE);
        }

        @Test
        @DisplayName("duplicate-text units in batch do not cause IllegalStateException")
        void batchEvaluateAndPromoteDuplicateTextUnitsHandledGracefully() {
            var unit1 = activeUnit("p1", "The temple demands a blood tithe", 0.9);
            var unit2 = activeUnit("p2", "The temple demands a blood tithe", 0.9); // same text

            var node = new PropositionNode(
                    "p1", CONTEXT_ID, "The temple demands a blood tithe",
                    0.9, 0.0, null, List.of(),
                    java.time.Instant.now(), java.time.Instant.now(),
                    com.embabel.dice.proposition.PropositionStatus.ACTIVE,
                    null, List.of());

            when(duplicateDetector.batchIsDuplicate(anyList(), anyList()))
                    .thenReturn(Map.of("The temple demands a blood tithe", false));
            when(engine.batchDetectConflicts(eq(CONTEXT_ID), anyList()))
                    .thenReturn(Map.of("The temple demands a blood tithe", List.of()));
            when(repository.findPropositionNodeById(anyString())).thenReturn(Optional.of(node));
            when(trustPipeline.batchEvaluate(anyList())).thenReturn(
                    Map.of("The temple demands a blood tithe", autoPromoteScore()));

            // Must not throw IllegalStateException: Duplicate key
            var result = promoter.batchEvaluateAndPromote(CONTEXT_ID, List.of(unit1, unit2));

            // Only one should be promoted (first wins, duplicate discarded)
            assertThat(result).isEqualTo(1);
        }

        @Test
        @DisplayName("unit with no node found still allowed through trust gate")
        void batchEvaluateAndPromoteNoNodeAllowed() {
            var unit = activeUnit("p1", "Fact without node", 0.9);

            when(duplicateDetector.batchIsDuplicate(anyList(), anyList()))
                    .thenReturn(Map.of("Fact without node", false));
            when(engine.batchDetectConflicts(eq(CONTEXT_ID), anyList()))
                    .thenReturn(Map.of("Fact without node", List.of()));
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.empty());
            // node is null so no TrustContext added — batchEvaluate returns empty map
            when(trustPipeline.batchEvaluate(anyList())).thenReturn(Map.of());

            var result = promoter.batchEvaluateAndPromote(CONTEXT_ID, List.of(unit));

            // score == null means allow (matches sequential behavior)
            assertThat(result).isEqualTo(1);
            verify(engine).promote("p1", INITIAL_RANK);
        }
    }
}
