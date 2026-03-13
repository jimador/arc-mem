package dev.arcmem.simulator.trust;

import com.embabel.dice.proposition.PropositionStatus;
import dev.arcmem.core.persistence.PropositionNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("SourceAuthoritySignal")
class SourceAuthoritySignalTest {

    private static final String CONTEXT_ID = "test-context";

    private final SourceAuthoritySignal signal = new SourceAuthoritySignal(
            new SourceAuthorityProperties(
                    Map.of("system", 1.0, "dm", 0.9, "player", 0.3),
                    0.5
            )
    );

    private PropositionNode proposition(String text, double confidence) {
        return new PropositionNode(UUID.randomUUID().toString(), CONTEXT_ID, text, confidence, 0.0, null, List.of(),
                Instant.now(), Instant.now(), PropositionStatus.ACTIVE, null, List.of());
    }

    private PropositionNode propositionWithSources(String text, double confidence, List<String> sourceIds) {
        return new PropositionNode(UUID.randomUUID().toString(), CONTEXT_ID, text, confidence, 0.0, null, List.of(),
                Instant.now(), Instant.now(), PropositionStatus.ACTIVE, null, sourceIds);
    }

    @Nested
    @DisplayName("evaluate")
    class Evaluate {

        @Test
        @DisplayName("DM source scores 0.9")
        void evaluate_dmSource_scores09() {
            var prop = propositionWithSources("test", 0.8, List.of("dm-narrator"));

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isCloseTo(0.9, within(0.001));
        }

        @Test
        @DisplayName("player-only source scores 0.5 (player 0.3 does not exceed neutral default)")
        void evaluate_playerOnlySource_scoresNeutral() {
            var prop = propositionWithSources("test", 0.8, List.of("player-alice"));

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isCloseTo(0.5, within(0.001));
        }

        @Test
        @DisplayName("system source scores 1.0")
        void evaluate_systemSource_scores10() {
            var prop = propositionWithSources("test", 0.8, List.of("system-init"));

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("null sourceIds returns default score")
        void evaluate_nullSourceIds_returnsDefault() {
            var prop = proposition("test", 0.8);
            prop.setSourceIds(null);

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isCloseTo(0.5, within(0.001));
        }

        @Test
        @DisplayName("empty sourceIds returns default score")
        void evaluate_emptySourceIds_returnsDefault() {
            var prop = propositionWithSources("test", 0.8, List.of());

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isCloseTo(0.5, within(0.001));
        }

        @Test
        @DisplayName("mixed sources returns the highest score")
        void evaluate_mixedSources_returnsHighest() {
            var prop = propositionWithSources("test", 0.8, List.of("player-bob", "dm-narrator"));

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isCloseTo(0.9, within(0.001));
        }

        @Test
        @DisplayName("unknown source prefix returns default score")
        void evaluate_unknownSource_returnsDefault() {
            var prop = propositionWithSources("test", 0.8, List.of("npc-vendor"));

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isCloseTo(0.5, within(0.001));
        }

        @Test
        @DisplayName("case insensitive source matching")
        void evaluate_uppercaseSource_matchesCaseInsensitively() {
            var prop = propositionWithSources("test", 0.8, List.of("DM-narrator"));

            var result = signal.evaluate(prop, CONTEXT_ID);

            assertThat(result).isPresent();
            assertThat(result.getAsDouble()).isCloseTo(0.9, within(0.001));
        }
    }

    @Nested
    @DisplayName("name")
    class Name {

        @Test
        @DisplayName("signal name is sourceAuthority")
        void name_returnsSourceAuthority() {
            assertThat(signal.name()).isEqualTo("sourceAuthority");
        }
    }
}
