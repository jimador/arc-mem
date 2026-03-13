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
import dev.arcmem.core.memory.event.ArchiveReason;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticUnitPromoter")
class SemanticUnitPromoterTest {

    private static final String CONTEXT_ID = "test-ctx";
    private static final double THRESHOLD = 0.65;
    private static final int INITIAL_RANK = 500;

    @Mock private ArcMemEngine engine;
    @Mock private TrustPipeline trustPipeline;
    @Mock private MemoryUnitRepository repository;
    @Mock private DuplicateDetector duplicateDetector;

    private SemanticUnitPromoter promoter;

    @BeforeEach
    void setUp() {
        var unitConfig = new ArcMemProperties.UnitConfig(20, INITIAL_RANK, 100, 900, true, THRESHOLD, DedupStrategy.FAST_THEN_LLM, CompliancePolicyMode.TIERED, true, true, true, 0.6, 400, 200, null, null, null, null, null);
        var properties = new ArcMemProperties(
                unitConfig, null, null, null, new ArcMemProperties.AssemblyConfig(0, false, EnforcementStrategy.PROMPT_ONLY), null, null, null, null, null, null, null, new ArcMemProperties.LlmCallConfig(30, 10)
        );
        promoter = new SemanticUnitPromoter(engine, properties, trustPipeline, repository, duplicateDetector,
                Optional.empty(), Optional.empty());
    }

    private SemanticUnit activeUnit(String id, String text, double confidence) {
        return new SemanticUnit() {
            @Override public String id() { return id; }
            @Override public String text() { return text; }
            @Override public double confidence() { return confidence; }
            @Override public boolean isPromotionCandidate() { return true; }
        };
    }

    private SemanticUnit nonCandidateUnit(String id, String text, double confidence) {
        return new SemanticUnit() {
            @Override public String id() { return id; }
            @Override public String text() { return text; }
            @Override public double confidence() { return confidence; }
            @Override public boolean isPromotionCandidate() { return false; }
        };
    }

    private PropositionNode mockNode() {
        return mock(PropositionNode.class);
    }

    private TrustScore autoPromoteScore() {
        return new TrustScore(0.8, Authority.RELIABLE, PromotionZone.AUTO_PROMOTE,
                              Map.of(), Instant.now());
    }

    private void allowPromotion(String propId) {
        when(duplicateDetector.isDuplicate(anyString(), anyList())).thenReturn(false);
        when(engine.detectConflicts(eq(CONTEXT_ID), anyString())).thenReturn(List.of());
        var node = mockNode();
        when(repository.findPropositionNodeById(propId)).thenReturn(Optional.of(node));
        when(trustPipeline.evaluate(eq(node), eq(CONTEXT_ID))).thenReturn(autoPromoteScore());
    }

    @Nested
    @DisplayName("confidence gate")
    class ConfidenceGate {

        @Test
        @DisplayName("skips unit below confidence threshold")
        void belowThresholdSkipped() {
            var unit = activeUnit("p1", "Low confidence fact", 0.3);

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(unit));

            assertThat(result).isZero();
            verify(duplicateDetector, never()).isDuplicate(anyString(), anyList());
        }

        @Test
        @DisplayName("passes unit at threshold")
        void atThresholdPasses() {
            var unit = activeUnit("p1", "At threshold fact", THRESHOLD);
            allowPromotion("p1");

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(unit));

            assertThat(result).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("duplicate detection gate")
    class DuplicateDetectionGate {

        @Test
        @DisplayName("rejects duplicate unit")
        void duplicateRejected() {
            var unit = activeUnit("p1", "Duplicate fact", 0.9);
            when(duplicateDetector.isDuplicate(eq("Duplicate fact"), anyList())).thenReturn(true);

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(unit));

            assertThat(result).isZero();
            verify(engine, never()).detectConflicts(anyString(), anyString());
        }

        @Test
        @DisplayName("passes novel unit to conflict detection")
        void novelPassesToConflict() {
            var unit = activeUnit("p1", "Novel fact", 0.9);
            allowPromotion("p1");

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(unit));

            assertThat(result).isEqualTo(1);
            verify(engine).detectConflicts(CONTEXT_ID, "Novel fact");
        }
    }

    @Nested
    @DisplayName("conflict resolution gate")
    class ConflictResolutionGate {

        private ConflictDetector.Conflict makeConflict(String unitId, String unitText) {
            var existingUnit = MemoryUnit.withoutTrust(
                    unitId, unitText, 700, Authority.PROVISIONAL, false, 0.8, 0
            );
            return new ConflictDetector.Conflict(existingUnit, "incoming text", 0.9, "negation");
        }

        @Test
        @DisplayName("KEEP_EXISTING rejects incoming unit")
        void keepExistingRejectsIncoming() {
            var unit = activeUnit("p1", "incoming text", 0.9);
            when(duplicateDetector.isDuplicate(eq("incoming text"), anyList())).thenReturn(false);
            var conflict = makeConflict("a1", "existing text");
            when(engine.detectConflicts(CONTEXT_ID, "incoming text")).thenReturn(List.of(conflict));
            when(engine.resolveConflict(eq(conflict), any(ResolutionContext.class))).thenReturn(ConflictResolver.Resolution.KEEP_EXISTING);

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(unit));

            assertThat(result).isZero();
            verify(engine, never()).promote(anyString(), anyInt());
        }

        @Test
        @DisplayName("REPLACE archives existing memory unit and promotes incoming")
        void replaceArchivesExistingAndPromotes() {
            var unit = activeUnit("p1", "incoming text", 0.9);
            when(duplicateDetector.isDuplicate(eq("incoming text"), anyList())).thenReturn(false);
            var conflict = makeConflict("a1", "existing text");
            when(engine.detectConflicts(CONTEXT_ID, "incoming text")).thenReturn(List.of(conflict));
            when(engine.resolveConflict(eq(conflict), any(ResolutionContext.class))).thenReturn(ConflictResolver.Resolution.REPLACE);
            var node = mockNode();
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.of(node));
            when(trustPipeline.evaluate(eq(node), eq(CONTEXT_ID))).thenReturn(autoPromoteScore());

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(unit));

            assertThat(result).isEqualTo(1);
            verify(engine).supersede("a1", "p1", ArchiveReason.CONFLICT_REPLACEMENT);
            verify(engine).promote("p1", INITIAL_RANK, Authority.RELIABLE);
        }

        @Test
        @DisplayName("COEXIST allows promotion to proceed")
        void coexistAllowsPromotion() {
            var unit = activeUnit("p1", "incoming text", 0.9);
            when(duplicateDetector.isDuplicate(eq("incoming text"), anyList())).thenReturn(false);
            var conflict = makeConflict("a1", "existing text");
            when(engine.detectConflicts(CONTEXT_ID, "incoming text")).thenReturn(List.of(conflict));
            when(engine.resolveConflict(eq(conflict), any(ResolutionContext.class))).thenReturn(ConflictResolver.Resolution.COEXIST);
            var node = mockNode();
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.of(node));
            when(trustPipeline.evaluate(eq(node), eq(CONTEXT_ID))).thenReturn(autoPromoteScore());

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(unit));

            assertThat(result).isEqualTo(1);
            verify(engine, never()).archive(anyString(), any());
        }

        @Test
        @DisplayName("KEEP_EXISTING takes precedence over COEXIST in multiple conflicts")
        void keepTakesPrecedenceOverCoexist() {
            var unit = activeUnit("p1", "incoming text", 0.9);
            when(duplicateDetector.isDuplicate(eq("incoming text"), anyList())).thenReturn(false);
            var conflict1 = makeConflict("a1", "unit 1");
            var conflict2 = makeConflict("a2", "unit 2");
            when(engine.detectConflicts(CONTEXT_ID, "incoming text"))
                    .thenReturn(List.of(conflict1, conflict2));
            when(engine.resolveConflict(eq(conflict1), any(ResolutionContext.class))).thenReturn(ConflictResolver.Resolution.COEXIST);
            when(engine.resolveConflict(eq(conflict2), any(ResolutionContext.class))).thenReturn(ConflictResolver.Resolution.KEEP_EXISTING);

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(unit));

            assertThat(result).isZero();
        }
    }

    @Nested
    @DisplayName("pipeline gate ordering")
    class GateOrdering {

        @Test
        @DisplayName("dedup runs before conflict detection")
        void dedupBeforeConflict() {
            var unit = activeUnit("p1", "text", 0.9);
            when(duplicateDetector.isDuplicate(eq("text"), anyList())).thenReturn(true);

            promoter.evaluateAndPromote(CONTEXT_ID, List.of(unit));

            verify(engine, never()).detectConflicts(anyString(), anyString());
        }

        @Test
        @DisplayName("conflict runs before trust evaluation")
        void conflictBeforeTrust() {
            var unit = activeUnit("p1", "text", 0.9);
            when(duplicateDetector.isDuplicate(eq("text"), anyList())).thenReturn(false);
            var conflict = new ConflictDetector.Conflict(
                    MemoryUnit.withoutTrust("a1", "old", 700, Authority.RELIABLE, false, 0.9, 0),
                    "text", 0.9, "negation"
            );
            when(engine.detectConflicts(CONTEXT_ID, "text")).thenReturn(List.of(conflict));
            when(engine.resolveConflict(eq(conflict), any(ResolutionContext.class))).thenReturn(ConflictResolver.Resolution.KEEP_EXISTING);

            promoter.evaluateAndPromote(CONTEXT_ID, List.of(unit));

            verify(trustPipeline, never()).evaluate(any(), anyString());
        }
    }

    @Nested
    @DisplayName("trust ceiling enforcement")
    class TrustCeilingEnforcement {

        @Test
        @DisplayName("ceiling=PROVISIONAL limits initial authority passed to engine.promote()")
        void ceilingLimitsInitialAuthority() {
            var unit = activeUnit("p1", "Valid fact", 0.9);
            when(duplicateDetector.isDuplicate(eq("Valid fact"), anyList())).thenReturn(false);
            when(engine.detectConflicts(CONTEXT_ID, "Valid fact")).thenReturn(List.of());
            var node = mockNode();
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.of(node));
            var ceiledScore = new TrustScore(0.8, Authority.PROVISIONAL, PromotionZone.AUTO_PROMOTE,
                    Map.of(), java.time.Instant.now());
            when(trustPipeline.evaluate(eq(node), eq(CONTEXT_ID))).thenReturn(ceiledScore);

            promoter.evaluateAndPromote(CONTEXT_ID, List.of(unit));

            verify(engine).promote("p1", INITIAL_RANK, Authority.PROVISIONAL);
        }

        @Test
        @DisplayName("ceiling=RELIABLE passes RELIABLE authority ceiling to engine.promote()")
        void ceilingAboveAssignedHasNoEffect() {
            var unit = activeUnit("p1", "Valid fact", 0.9);
            when(duplicateDetector.isDuplicate(eq("Valid fact"), anyList())).thenReturn(false);
            when(engine.detectConflicts(CONTEXT_ID, "Valid fact")).thenReturn(List.of());
            var node = mockNode();
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.of(node));
            var reliableScore = new TrustScore(0.8, Authority.RELIABLE, PromotionZone.AUTO_PROMOTE,
                    Map.of(), java.time.Instant.now());
            when(trustPipeline.evaluate(eq(node), eq(CONTEXT_ID))).thenReturn(reliableScore);

            promoter.evaluateAndPromote(CONTEXT_ID, List.of(unit));

            verify(engine).promote("p1", INITIAL_RANK, Authority.RELIABLE);
        }

        @Test
        @DisplayName("no trust score (node not found) uses two-arg promote()")
        void noTrustScoreUsesTwoArgPromote() {
            var unit = activeUnit("p1", "Valid fact", 0.9);
            when(duplicateDetector.isDuplicate(eq("Valid fact"), anyList())).thenReturn(false);
            when(engine.detectConflicts(CONTEXT_ID, "Valid fact")).thenReturn(List.of());
            when(repository.findPropositionNodeById("p1")).thenReturn(Optional.empty());

            promoter.evaluateAndPromote(CONTEXT_ID, List.of(unit));

            verify(engine).promote("p1", INITIAL_RANK);
            verify(engine, never()).promote(eq("p1"), anyInt(), any());
        }
    }

    @Nested
    @DisplayName("Trust re-evaluation triggers")
    class TrustReEvaluationTriggers {

        @Test
        @DisplayName("KEEP_EXISTING resolution triggers trust re-evaluation on kept memory unit")
        void keepExistingResolutionTriggersTrustReEvaluation() {
            var unit = activeUnit("p1", "incoming text", 0.9);
            when(duplicateDetector.isDuplicate(eq("incoming text"), anyList())).thenReturn(false);
            var existingUnit = MemoryUnit.withoutTrust("a1", "existing text", 700, Authority.PROVISIONAL, false, 0.8, 0);
            var conflict = new ConflictDetector.Conflict(existingUnit, "incoming text", 0.9, "negation");
            when(engine.detectConflicts(CONTEXT_ID, "incoming text")).thenReturn(List.of(conflict));
            when(engine.resolveConflict(eq(conflict), any(ResolutionContext.class))).thenReturn(ConflictResolver.Resolution.KEEP_EXISTING);

            promoter.evaluateAndPromote(CONTEXT_ID, List.of(unit));

            verify(engine).reEvaluateTrust("a1");
        }
    }

    @Nested
    @DisplayName("conflict pre-check gate")
    class ConflictPreCheckGate {

        private ConflictIndex mockIndex;
        private MemoryUnit reliableUnit;

        @org.junit.jupiter.api.BeforeEach
        void setUpPrecheck() {
            mockIndex = mock(ConflictIndex.class);
            reliableUnit = MemoryUnit.withoutTrust("a1", "established text", 700, Authority.RELIABLE, false, 0.9, 0);
        }

        private SemanticUnitPromoter promoterWithIndex(ConflictIndex index) {
            var unitConfig = new ArcMemProperties.UnitConfig(20, INITIAL_RANK, 100, 900, true, THRESHOLD,
                    DedupStrategy.FAST_THEN_LLM, CompliancePolicyMode.TIERED, true, true, true, 0.6, 400, 200,
                    null, null, null, null, null);
            var properties = new ArcMemProperties(
                    unitConfig, null, null, null,
                    new ArcMemProperties.AssemblyConfig(0, false, EnforcementStrategy.PROMPT_ONLY), null, null, null, null, null, null, null, new ArcMemProperties.LlmCallConfig(30, 10));
            return new SemanticUnitPromoter(engine, properties, trustPipeline, repository, duplicateDetector,
                    Optional.of(index), Optional.empty());
        }

        @Test
        @DisplayName("filters unit conflicting with RELIABLE memory unit")
        void precheckFiltersUnitConflictingWithReliableUnit() {
            when(mockIndex.size()).thenReturn(1);
            when(engine.inject(CONTEXT_ID)).thenReturn(List.of(reliableUnit));
            var entry = new ConflictEntry("a1", "incoming proposition", Authority.RELIABLE,
                    ConflictType.CONTRADICTION, 0.9, Instant.now());
            when(mockIndex.getConflicts("a1")).thenReturn(Set.of(entry));

            var unit = activeUnit("p1", "incoming proposition", 0.9);
            var precheck = promoterWithIndex(mockIndex);

            var result = precheck.evaluateAndPromote(CONTEXT_ID, List.of(unit));

            assertThat(result).isZero();
            verify(duplicateDetector, never()).isDuplicate(anyString(), anyList());
        }

        @Test
        @DisplayName("passes unit conflicting with PROVISIONAL memory unit")
        void precheckPassesUnitConflictingWithProvisionalUnit() {
            when(mockIndex.size()).thenReturn(1);
            var provisionalUnit = MemoryUnit.withoutTrust("a1", "established text", 500, Authority.PROVISIONAL,
                    false, 0.8, 0);
            when(engine.inject(CONTEXT_ID)).thenReturn(List.of(provisionalUnit));
            var entry = new ConflictEntry("a1", "incoming proposition", Authority.PROVISIONAL,
                    ConflictType.CONTRADICTION, 0.7, Instant.now());
            when(mockIndex.getConflicts("a1")).thenReturn(Set.of(entry));

            var unit = activeUnit("p1", "incoming proposition", 0.9);
            allowPromotion("p1");
            var precheck = promoterWithIndex(mockIndex);

            var result = precheck.evaluateAndPromote(CONTEXT_ID, List.of(unit));

            assertThat(result).isEqualTo(1);
            verify(duplicateDetector).isDuplicate(anyString(), anyList());
        }

        @Test
        @DisplayName("skips pre-check when index is empty")
        void precheckSkippedWhenIndexIsEmpty() {
            when(mockIndex.size()).thenReturn(0);
            var unit = activeUnit("p1", "Any proposition", 0.9);
            allowPromotion("p1");
            var precheck = promoterWithIndex(mockIndex);

            var result = precheck.evaluateAndPromote(CONTEXT_ID, List.of(unit));

            assertThat(result).isEqualTo(1);
            verify(mockIndex, never()).getConflicts(anyString());
        }

        @Test
        @DisplayName("skips pre-check when index is absent")
        void precheckSkippedWhenIndexIsAbsent() {
            var unit = activeUnit("p1", "Any proposition", 0.9);
            allowPromotion("p1");

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(unit));

            assertThat(result).isEqualTo(1);
        }

        @Test
        @DisplayName("batch path filters units conflicting with RELIABLE memory unit")
        void batchPathIncludesPrecheck() {
            when(mockIndex.size()).thenReturn(1);
            var existingUnit = MemoryUnit.withoutTrust("a1", "established text", 700, Authority.RELIABLE,
                    false, 0.9, 0);
            when(engine.findByContext(CONTEXT_ID)).thenReturn(List.of(existingUnit));
            var entry = new ConflictEntry("a1", "conflicting proposition", Authority.RELIABLE,
                    ConflictType.CONTRADICTION, 0.9, Instant.now());
            when(mockIndex.getConflicts("a1")).thenReturn(Set.of(entry));

            var unit = activeUnit("p1", "conflicting proposition", 0.9);
            var precheck = promoterWithIndex(mockIndex);

            var result = precheck.batchEvaluateAndPromote(CONTEXT_ID, List.of(unit));

            assertThat(result).isZero();
            verify(duplicateDetector, never()).batchIsDuplicate(anyList(), anyList());
        }
    }

    @Nested
    @DisplayName("full pipeline")
    class FullPipeline {

        @Test
        @DisplayName("promotes unit passing all gates")
        void fullPipelinePromotes() {
            var unit = activeUnit("p1", "Valid fact", 0.9);
            allowPromotion("p1");

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(unit));

            assertThat(result).isEqualTo(1);
            verify(engine).promote("p1", INITIAL_RANK, Authority.RELIABLE);
        }

        @Test
        @DisplayName("skips non-candidate units")
        void nonCandidateSkipped() {
            var unit = nonCandidateUnit("p1", "text", 0.9);

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(unit));

            assertThat(result).isZero();
        }

        @Test
        @DisplayName("PropositionSemanticUnit adapter works through the pipeline")
        void propositionAdapterWorks() {
            var prop = mock(Proposition.class);
            lenient().when(prop.getId()).thenReturn("p1");
            lenient().when(prop.getText()).thenReturn("already revised fact");
            when(prop.getConfidence()).thenReturn(0.9);
            when(prop.getStatus()).thenReturn(PropositionStatus.PROMOTED);
            var adapted = new PropositionSemanticUnit(prop);
            allowPromotion("p1");

            var result = promoter.evaluateAndPromote(CONTEXT_ID, List.of(adapted));

            assertThat(result).isEqualTo(1);
            verify(engine).promote("p1", INITIAL_RANK, Authority.RELIABLE);
        }

        @Test
        @DisplayName("processes multiple units independently")
        void multipleUnitsIndependent() {
            var good = activeUnit("p1", "Good fact", 0.9);
            var dup = activeUnit("p2", "Dup fact", 0.9);

            when(duplicateDetector.isDuplicate(eq("Good fact"), anyList())).thenReturn(false);
            when(duplicateDetector.isDuplicate(eq("Dup fact"), anyList())).thenReturn(true);
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
