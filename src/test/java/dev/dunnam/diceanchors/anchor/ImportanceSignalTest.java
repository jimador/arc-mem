package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.persistence.PropositionNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@DisplayName("ImportanceSignal")
@ExtendWith(MockitoExtension.class)
class ImportanceSignalTest {

    private static final String CONTEXT_ID = "test-ctx";

    @Mock
    private AnchorEngine anchorEngine;

    @Mock
    private DiceAnchorsProperties properties;

    @Mock
    private DiceAnchorsProperties.AnchorConfig anchorConfig;

    @Mock
    private DiceAnchorsProperties.QualityScoringConfig qualityScoringConfig;

    private ImportanceSignal signal;

    @BeforeEach
    void setUp() {
        lenient().when(properties.anchor()).thenReturn(anchorConfig);
        lenient().when(anchorConfig.qualityScoring()).thenReturn(qualityScoringConfig);
        lenient().when(qualityScoringConfig.enabled()).thenReturn(true);
        signal = new ImportanceSignal(anchorEngine, properties);
    }

    private static Anchor anchor(String id, String text) {
        return Anchor.withoutTrust(id, text, 500, Authority.RELIABLE, false, 0.9, 1);
    }

    private static PropositionNode proposition(String text) {
        return new PropositionNode(text, 0.8);
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
            when(anchorConfig.qualityScoring()).thenReturn(null);
            var prop = proposition("The dragon guards the gate");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("empty anchor context")
    class EmptyContext {

        @Test
        @DisplayName("returns 0.5 neutral importance when no active anchors exist")
        void noAnchorsReturnsNeutral() {
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(List.of());
            var prop = proposition("The artifact requires sacrifice");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isEqualTo(0.5);
        }
    }

    @Nested
    @DisplayName("importance scoring")
    class ImportanceScoring {

        @Test
        @DisplayName("proposition with full keyword overlap yields importance close to 1.0")
        void fullOverlapYieldsHighImportance() {
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(List.of(
                    anchor("a-1", "dragon guards eastern gate"),
                    anchor("a-2", "guardian stands watch")));
            // All non-stop tokens in proposition appear in context
            var prop = proposition("eastern dragon guards gate");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isGreaterThan(0.6);
        }

        @Test
        @DisplayName("proposition with no keyword overlap yields importance 0.0")
        void noOverlapYieldsZeroImportance() {
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(List.of(
                    anchor("a-1", "dungeon traps surround"),
                    anchor("a-2", "corridors twist underground")));
            // "weather" and "capital" don't appear in anchors
            var prop = proposition("weather capital mild pleasant");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isLessThan(0.3);
        }

        @Test
        @DisplayName("partial keyword overlap yields score between 0.0 and 1.0")
        void partialOverlapYieldsMidRangeScore() {
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(List.of(
                    anchor("a-1", "dragon breathes fire")));
            // "dragon" overlaps; "hoards", "gold" don't
            var prop = proposition("dragon hoards gold");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isGreaterThan(0.0).isLessThan(1.0);
        }

        @Test
        @DisplayName("score is clamped to [0.0, 1.0]")
        void scoreIsClampedToRange() {
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(List.of(
                    anchor("a-1", "dragon guardian gate tower castle")));
            var prop = proposition("dragon guardian gate");

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isBetween(0.0, 1.0);
        }
    }

    @Nested
    @DisplayName("name()")
    class NameMethod {

        @Test
        @DisplayName("returns 'importance'")
        void nameIsImportance() {
            assertThat(signal.name()).isEqualTo("importance");
        }
    }
}
