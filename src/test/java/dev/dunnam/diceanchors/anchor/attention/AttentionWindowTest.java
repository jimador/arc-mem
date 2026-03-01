package dev.dunnam.diceanchors.anchor.attention;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("AttentionWindow")
class AttentionWindowTest {

    private static final Instant NOW = Instant.parse("2026-02-28T12:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(5);

    @Nested
    @DisplayName("heatScore")
    class HeatScore {

        @Test
        @DisplayName("normalizes by max expected events")
        void normalizesCorrectly() {
            var window = new AttentionWindow("a1", "ctx", 0, 0, 10, NOW, NOW, List.of(NOW));
            assertThat(window.heatScore(20)).isCloseTo(0.5, within(0.001));
        }

        @Test
        @DisplayName("clamps at 1.0 when over max")
        void clampsAtOne() {
            var window = new AttentionWindow("a1", "ctx", 0, 0, 30, NOW, NOW, List.of(NOW));
            assertThat(window.heatScore(20)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("returns 0.0 for empty window")
        void zeroForEmpty() {
            var window = new AttentionWindow("a1", "ctx", 0, 0, 0, NOW, NOW, List.of());
            assertThat(window.heatScore(20)).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("pressureScore")
    class PressureScore {

        @Test
        @DisplayName("returns 0.0 on empty window")
        void zeroOnEmpty() {
            var window = new AttentionWindow("a1", "ctx", 0, 0, 0, NOW, NOW, List.of());
            assertThat(window.pressureScore()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("computes conflict ratio correctly")
        void conflictRatio() {
            var window = new AttentionWindow("a1", "ctx", 3, 0, 6, NOW, NOW, List.of(NOW));
            assertThat(window.pressureScore()).isCloseTo(0.5, within(0.001));
        }
    }

    @Nested
    @DisplayName("burstFactor")
    class BurstFactor {

        @Test
        @DisplayName("returns 1.0 with fewer than 2 events")
        void singleEvent() {
            var window = AttentionWindow.initial("a1", "ctx", NOW);
            assertThat(window.burstFactor()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("uniform distribution returns approximately 1.0")
        void uniformDistribution() {
            var start = NOW.minus(Duration.ofMinutes(4));
            var timestamps = List.of(
                    start,
                    start.plusSeconds(30),
                    start.plusSeconds(60),
                    start.plusSeconds(90),
                    start.plusSeconds(120),
                    start.plusSeconds(150),
                    start.plusSeconds(180),
                    start.plusSeconds(210)
            );
            var window = new AttentionWindow("a1", "ctx", 0, 0, 8, timestamps.getLast(), start, timestamps);
            assertThat(window.burstFactor()).isCloseTo(1.0, within(0.2));
        }

        @Test
        @DisplayName("clustered events produce burst factor > 1.0")
        void clusteredEvents() {
            var start = NOW.minus(Duration.ofMinutes(4));
            var timestamps = List.of(
                    start,                        // minute 0
                    start.plusSeconds(30),         // minute 0.5
                    start.plusSeconds(181),        // minute 3.01 (last quarter)
                    start.plusSeconds(190),
                    start.plusSeconds(200),
                    start.plusSeconds(210),
                    start.plusSeconds(220),
                    start.plusSeconds(230)
            );
            var window = new AttentionWindow("a1", "ctx", 0, 0, 8, timestamps.getLast(), start, timestamps);
            assertThat(window.burstFactor()).isGreaterThan(1.5);
        }
    }

    @Nested
    @DisplayName("withEvent")
    class WithEvent {

        @Test
        @DisplayName("prunes timestamps outside window duration")
        void prunesExpiredTimestamps() {
            var t0 = NOW.minus(Duration.ofMinutes(10));
            var timestamps = List.of(t0, t0.plusSeconds(60), t0.plusSeconds(120));
            var window = new AttentionWindow("a1", "ctx", 1, 0, 3, t0.plusSeconds(120), t0, timestamps);

            var updated = window.withEvent(NOW, Duration.ofMinutes(5));

            // All three original timestamps are > 5 minutes before NOW, so all are pruned
            assertThat(updated.totalEventCount()).isEqualTo(1);
            assertThat(updated.eventTimestamps()).hasSize(1);
            assertThat(updated.eventTimestamps().getFirst()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("retains timestamps within window")
        void retainsRecentTimestamps() {
            var t1 = NOW.minus(Duration.ofMinutes(2));
            var t2 = NOW.minus(Duration.ofMinutes(1));
            var timestamps = List.of(t1, t2);
            var window = new AttentionWindow("a1", "ctx", 0, 0, 2, t2, t1, timestamps);

            var updated = window.withEvent(NOW, Duration.ofMinutes(5));

            assertThat(updated.totalEventCount()).isEqualTo(3);
            assertThat(updated.eventTimestamps()).hasSize(3);
        }

        @Test
        @DisplayName("withConflict increments conflictCount on top of pruned window")
        void withConflictIncrementsCount() {
            var t1 = NOW.minus(Duration.ofMinutes(1));
            var window = new AttentionWindow("a1", "ctx", 2, 0, 3, t1, t1.minusSeconds(30), List.of(t1));

            var updated = window.withConflict(NOW, WINDOW);

            assertThat(updated.conflictCount()).isGreaterThan(window.conflictCount());
            assertThat(updated.lastEventAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("withReinforcement increments reinforcementCount")
        void withReinforcementIncrementsCount() {
            var t1 = NOW.minus(Duration.ofMinutes(1));
            var window = new AttentionWindow("a1", "ctx", 0, 1, 2, t1, t1.minusSeconds(30), List.of(t1));

            var updated = window.withReinforcement(NOW, WINDOW);

            assertThat(updated.reinforcementCount()).isGreaterThan(window.reinforcementCount());
            assertThat(updated.lastEventAt()).isEqualTo(NOW);
        }
    }

    @Nested
    @DisplayName("static factories")
    class StaticFactories {

        @Test
        @DisplayName("initial creates fresh window with one general event")
        void initialCreatesGeneral() {
            var window = AttentionWindow.initial("a1", "ctx", NOW);

            assertThat(window.anchorId()).isEqualTo("a1");
            assertThat(window.contextId()).isEqualTo("ctx");
            assertThat(window.totalEventCount()).isEqualTo(1);
            assertThat(window.conflictCount()).isEqualTo(0);
            assertThat(window.reinforcementCount()).isEqualTo(0);
            assertThat(window.lastEventAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("initialConflict creates fresh window with conflictCount=1")
        void initialConflictSetsConflict() {
            var window = AttentionWindow.initialConflict("a1", "ctx", NOW);

            assertThat(window.conflictCount()).isEqualTo(1);
            assertThat(window.totalEventCount()).isEqualTo(1);
            assertThat(window.reinforcementCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("initialReinforcement creates fresh window with reinforcementCount=1")
        void initialReinforcementSetsReinforcement() {
            var window = AttentionWindow.initialReinforcement("a1", "ctx", NOW);

            assertThat(window.reinforcementCount()).isEqualTo(1);
            assertThat(window.totalEventCount()).isEqualTo(1);
            assertThat(window.conflictCount()).isEqualTo(0);
        }
    }
}
