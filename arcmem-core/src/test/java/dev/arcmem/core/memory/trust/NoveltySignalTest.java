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
import dev.arcmem.core.config.ArcMemProperties;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@DisplayName("NoveltySignal")
@ExtendWith(MockitoExtension.class)
class NoveltySignalTest {

    private static final String CONTEXT_ID = "test-ctx";

    @Mock
    private ArcMemEngine arcMemEngine;

    @Mock
    private ArcMemProperties properties;

    @Mock
    private ArcMemProperties.UnitConfig unitConfig;

    @Mock
    private ArcMemProperties.QualityScoringConfig qualityScoringConfig;

    private NoveltySignal signal;

    @BeforeEach
    void setUp() {
        lenient().when(properties.unit()).thenReturn(unitConfig);
        lenient().when(unitConfig.qualityScoring()).thenReturn(qualityScoringConfig);
        lenient().when(qualityScoringConfig.enabled()).thenReturn(true);
        signal = new NoveltySignal(arcMemEngine, properties);
    }

    private static MemoryUnit unit(String id, String text) {
        return MemoryUnit.withoutTrust(id, text, 500, Authority.RELIABLE, false, 0.9, 1);
    }

    private static PropositionNode proposition(String text) {
        return new PropositionNode(UUID.randomUUID().toString(), "test-context", text, 0.8, 0.0, null, List.of(),
                Instant.now(), Instant.now(), PropositionStatus.ACTIVE, null, List.of());
    }

    @Nested
    @DisplayName("disabled configuration")
    class DisabledConfig {

        @Test
        @DisplayName("returns empty when quality scoring is disabled")
        void disabledReturnsEmpty() {
            when(qualityScoringConfig.enabled()).thenReturn(false);
            var prop = proposition("The dragon guards the gate");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when qualityScoring config is null")
        void nullConfigReturnsEmpty() {
            when(unitConfig.qualityScoring()).thenReturn(null);
            var prop = proposition("The dragon guards the gate");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("empty memory unit context")
    class EmptyContext {

        @Test
        @DisplayName("returns 1.0 when no active memory units exist")
        void noUnitsReturnsMaxNovelty() {
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(List.of());
            var prop = proposition("The artifact requires a blood sacrifice");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("novelty scoring")
    class NoveltyScoring {

        @Test
        @DisplayName("identical proposition to memory unit yields novelty 0.0")
        void identicalPropositionYieldsZeroNovelty() {
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(
                    List.of(unit("a-1", "The dragon guards the eastern gate")));
            var prop = proposition("the dragon guards the eastern gate");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("completely novel proposition yields novelty 1.0")
        void completelyNovelPropositionYieldsMaxNovelty() {
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(List.of(
                    unit("a-1", "rain falls heavily"),
                    unit("a-2", "mountains rise tall")));
            // "sacrifice" and "artifact" don't appear in units at all
            var prop = proposition("artifact requires sacrifice");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isGreaterThan(0.7);
        }

        @Test
        @DisplayName("partial overlap yields score between 0.0 and 1.0")
        void partialOverlapYieldsMidRangeScore() {
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(
                    List.of(unit("a-1", "dragon breathes fire")));
            var prop = proposition("dragon hoards gold");
            // "dragon" overlaps; "hoards", "gold" vs "breathes", "fire" do not

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isGreaterThan(0.0).isLessThan(1.0);
        }

        @Test
        @DisplayName("returns max across multiple memory units (closest match used)")
        void returnsMaxSimilarityAcrossUnits() {
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(List.of(
                    unit("a-1", "elves trade gems"),
                    unit("a-2", "dragon guards eastern gate")));
            var prop = proposition("dragon guards northern gate");
            // Closer to a-2 than a-1

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            // dragon and gate overlap with a-2, so novelty should be relatively low
            assertThat(result.getAsDouble()).isLessThan(0.7);
        }
    }

    @Nested
    @DisplayName("stop word filtering")
    class StopWordFiltering {

        @Test
        @DisplayName("stop words excluded — 'the dragon is in the cave' vs 'a dragon was in a cave' share content words")
        void stopWordsExcludedFromSimilarity() {
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(
                    List.of(unit("a-1", "a dragon was in a cave")));
            var prop = proposition("the dragon is in the cave");
            // After stop-word removal: {dragon, cave} vs {dragon, cave} -> identical

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            // Jaccard of identical sets = 1.0, so novelty = 0.0
            assertThat(result.getAsDouble()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("proposition of only stop words tokenizes to empty set")
        void stopWordsOnlyPropositionHandledGracefully() {
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(
                    List.of(unit("a-1", "the guardian is strong")));
            var prop = proposition("the and or is are");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isBetween(0.0, 1.0);
        }
    }

    @Nested
    @DisplayName("name()")
    class NameMethod {

        @Test
        @DisplayName("returns 'novelty'")
        void nameIsNovelty() {
            assertThat(signal.name()).isEqualTo("novelty");
        }
    }
}
