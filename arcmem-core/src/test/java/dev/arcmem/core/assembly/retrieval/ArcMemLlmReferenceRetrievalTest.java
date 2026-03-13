package dev.arcmem.core.assembly.retrieval;
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

import dev.arcmem.core.config.ArcMemProperties.RetrievalConfig;
import dev.arcmem.core.config.ArcMemProperties.ScoringConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("ArcMemLlmReference retrieval modes")
@ExtendWith(MockitoExtension.class)
class ArcMemLlmReferenceRetrievalTest {

    @Mock
    private ArcMemEngine engine;

    private final RelevanceScorer scorer = new RelevanceScorer();
    private final ScoringConfig defaultScoring = new ScoringConfig(0.4, 0.3, 0.3);

    private static List<MemoryUnit> mixedUnits() {
        var units = new ArrayList<MemoryUnit>();
        // 2 CANON units
        units.add(new MemoryUnit("canon1", "Canon fact one", 800, Authority.CANON, true, 0.9, 10,
                null, 0.0, 1.0, MemoryTier.HOT, null));
        units.add(new MemoryUnit("canon2", "Canon fact two", 850, Authority.CANON, true, 0.95, 12,
                null, 0.0, 1.0, MemoryTier.HOT, null));
        // 13 non-CANON units with varying authority/tier/confidence
        units.add(new MemoryUnit("rel1", "Reliable hot fact", 700, Authority.RELIABLE, false, 0.85, 7,
                null, 0.0, 1.0, MemoryTier.HOT, null));
        units.add(new MemoryUnit("rel2", "Reliable warm fact", 600, Authority.RELIABLE, false, 0.75, 5,
                null, 0.0, 1.0, MemoryTier.WARM, null));
        units.add(new MemoryUnit("rel3", "Reliable cold fact", 400, Authority.RELIABLE, false, 0.65, 3,
                null, 0.0, 1.0, MemoryTier.COLD, null));
        units.add(new MemoryUnit("unr1", "Unreliable hot fact", 500, Authority.UNRELIABLE, false, 0.7, 2,
                null, 0.0, 1.0, MemoryTier.HOT, null));
        units.add(new MemoryUnit("unr2", "Unreliable warm fact", 400, Authority.UNRELIABLE, false, 0.6, 1,
                null, 0.0, 1.0, MemoryTier.WARM, null));
        units.add(new MemoryUnit("unr3", "Unreliable cold fact", 300, Authority.UNRELIABLE, false, 0.5, 0,
                null, 0.0, 1.0, MemoryTier.COLD, null));
        units.add(new MemoryUnit("prov1", "Provisional hot fact", 400, Authority.PROVISIONAL, false, 0.6, 0,
                null, 0.0, 1.0, MemoryTier.HOT, null));
        units.add(new MemoryUnit("prov2", "Provisional warm fact", 350, Authority.PROVISIONAL, false, 0.5, 0,
                null, 0.0, 1.0, MemoryTier.WARM, null));
        units.add(new MemoryUnit("prov3", "Provisional cold fact", 200, Authority.PROVISIONAL, false, 0.4, 0,
                null, 0.0, 1.0, MemoryTier.COLD, null));
        units.add(new MemoryUnit("prov4", "Provisional cold low", 150, Authority.PROVISIONAL, false, 0.3, 0,
                null, 0.0, 1.0, MemoryTier.COLD, null));
        units.add(new MemoryUnit("prov5", "Provisional cold lowest", 120, Authority.PROVISIONAL, false, 0.25, 0,
                null, 0.0, 1.0, MemoryTier.COLD, null));
        units.add(new MemoryUnit("prov6", "Provisional cold extra", 110, Authority.PROVISIONAL, false, 0.2, 0,
                null, 0.0, 1.0, MemoryTier.COLD, null));
        return units;
    }

    @Nested
    @DisplayName("Bulk mode")
    class BulkMode {

        @Test
        @DisplayName("BULK mode returns all memory units within budget")
        void bulkModeReturnsAllUnits() {
            var units = mixedUnits();
            when(engine.inject(anyString())).thenReturn(units);

            var config = new RetrievalConfig(RetrievalMode.BULK, 0.0, 5, 5, defaultScoring);
            var ref = new ArcMemLlmReference(engine, "test-ctx", 20,
                    CompliancePolicy.tiered(), 0, null, null, config, scorer);

            var result = ref.getUnits();

            assertThat(result).hasSize(units.size());
        }

        @Test
        @DisplayName("null config behaves as BULK")
        void nullConfigBehavesAsBulk() {
            var units = mixedUnits();
            when(engine.inject(anyString())).thenReturn(units);

            var refNull = new ArcMemLlmReference(engine, "test-ctx", 20,
                    CompliancePolicy.tiered(), 0, null, null, null, scorer);
            var refBulk = new ArcMemLlmReference(engine, "test-ctx2", 20,
                    CompliancePolicy.tiered(), 0, null, null,
                    new RetrievalConfig(RetrievalMode.BULK, 0.0, 5, 5, defaultScoring), scorer);

            var resultNull = refNull.getUnits();
            var resultBulk = refBulk.getUnits();

            assertThat(resultNull).hasSameSizeAs(resultBulk);
        }
    }

    @Nested
    @DisplayName("Hybrid mode")
    class HybridMode {

        @Test
        @DisplayName("hybrid mode with baselineTopK=5 reduces non-CANON memory units to 5")
        void hybridModeReducesBaseline() {
            var units = mixedUnits(); // 2 CANON + 13 non-CANON = 15
            when(engine.inject(anyString())).thenReturn(units);

            var config = new RetrievalConfig(RetrievalMode.HYBRID, 0.0, 5, 5, defaultScoring);
            var ref = new ArcMemLlmReference(engine, "test-ctx", 20,
                    CompliancePolicy.tiered(), 0, null, null, config, scorer);

            var result = ref.getUnits();

            // 2 CANON always included + 5 top-k non-CANON = 7
            assertThat(result).hasSize(7);
        }

        @Test
        @DisplayName("CANON memory units always included in hybrid mode")
        void canonAlwaysIncludedInHybrid() {
            var units = mixedUnits();
            when(engine.inject(anyString())).thenReturn(units);

            var config = new RetrievalConfig(RetrievalMode.HYBRID, 0.0, 3, 5, defaultScoring);
            var ref = new ArcMemLlmReference(engine, "test-ctx", 20,
                    CompliancePolicy.tiered(), 0, null, null, config, scorer);

            var result = ref.getUnits();

            var canonIds = result.stream()
                    .filter(a -> a.authority() == Authority.CANON)
                    .map(MemoryUnit::id)
                    .toList();
            assertThat(canonIds).containsExactlyInAnyOrder("canon1", "canon2");
        }

        @Test
        @DisplayName("quality gate filters low relevance memory units")
        void qualityGateFiltersLowRelevance() {
            var units = mixedUnits();
            when(engine.inject(anyString())).thenReturn(units);

            // Set minRelevance high enough to filter out PROVISIONAL/COLD units
            var config = new RetrievalConfig(RetrievalMode.HYBRID, 0.5, 15, 5, defaultScoring);
            var ref = new ArcMemLlmReference(engine, "test-ctx", 20,
                    CompliancePolicy.tiered(), 0, null, null, config, scorer);

            var result = ref.getUnits();

            // All included non-CANON units should have heuristic score >= 0.5
            var nonCanonUnits = result.stream()
                    .filter(a -> a.authority() != Authority.CANON)
                    .toList();
            for (var unit : nonCanonUnits) {
                var score = scorer.computeHeuristicScore(unit, defaultScoring);
                assertThat(score).as("MemoryUnit %s should have score >= 0.5", unit.id())
                        .isGreaterThanOrEqualTo(0.5);
            }

            // CANON units are still present regardless
            assertThat(result.stream().filter(a -> a.authority() == Authority.CANON).count())
                    .isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Tool mode")
    class ToolMode {

        @Test
        @DisplayName("TOOL mode returns empty baseline")
        void toolModeReturnsEmptyBaseline() {
            var config = new RetrievalConfig(RetrievalMode.TOOL, 0.0, 5, 5, defaultScoring);
            var ref = new ArcMemLlmReference(engine, "test-ctx", 20,
                    CompliancePolicy.tiered(), 0, null, null, config, scorer);

            var result = ref.getUnits();

            assertThat(result).isEmpty();
        }
    }
}
