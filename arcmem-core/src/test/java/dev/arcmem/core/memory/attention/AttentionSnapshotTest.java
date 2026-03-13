package dev.arcmem.core.memory.attention;
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("AttentionSnapshot")
class AttentionSnapshotTest {

    private static final Instant NOW = Instant.parse("2026-02-28T12:00:00Z");

    @Nested
    @DisplayName("of single window")
    class SingleWindow {

        @Test
        @DisplayName("captures correct metrics from window")
        void capturesMetrics() {
            var window = new AttentionWindow("a1", "ctx", 3, 2, 10, NOW, NOW.minusSeconds(60),
                    List.of(NOW.minusSeconds(60), NOW));
            var snapshot = AttentionSnapshot.of(window, 20);

            assertThat(snapshot.heatScore()).isCloseTo(0.5, within(0.001));
            assertThat(snapshot.pressureScore()).isCloseTo(0.3, within(0.001));
            assertThat(snapshot.conflictCount()).isEqualTo(3);
            assertThat(snapshot.reinforcementCount()).isEqualTo(2);
            assertThat(snapshot.totalEventCount()).isEqualTo(10);
            assertThat(snapshot.unitIds()).containsExactly("a1");
        }

        @Test
        @DisplayName("burst factor is derived from window timestamps")
        void burstFactorFromWindow() {
            var window = AttentionWindow.initial("a1", "ctx", NOW);
            var snapshot = AttentionSnapshot.of(window, 10);

            // Single event → burstFactor == 1.0
            assertThat(snapshot.burstFactor()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("window duration reflects span between windowStart and lastEventAt")
        void windowDurationReflectsSpan() {
            var start = NOW.minusSeconds(90);
            var window = new AttentionWindow("a1", "ctx", 0, 0, 2, NOW, start, List.of(start, NOW));
            var snapshot = AttentionSnapshot.of(window, 20);

            assertThat(snapshot.windowDuration().toSeconds()).isEqualTo(90);
        }
    }

    @Nested
    @DisplayName("ofCluster")
    class Cluster {

        @Test
        @DisplayName("aggregates means across windows")
        void aggregatesMeans() {
            var w1 = new AttentionWindow("a1", "ctx", 2, 0, 8, NOW, NOW.minusSeconds(60), List.of(NOW));
            var w2 = new AttentionWindow("a2", "ctx", 0, 2, 4, NOW, NOW.minusSeconds(60), List.of(NOW));
            var w3 = new AttentionWindow("a3", "ctx", 1, 1, 6, NOW, NOW.minusSeconds(60), List.of(NOW));

            var snapshot = AttentionSnapshot.ofCluster(List.of(w1, w2, w3), 20);

            assertThat(snapshot.unitIds()).containsExactlyInAnyOrder("a1", "a2", "a3");
            assertThat(snapshot.conflictCount()).isEqualTo(3);
            assertThat(snapshot.reinforcementCount()).isEqualTo(3);
            assertThat(snapshot.totalEventCount()).isEqualTo(18);
            // Heat scores: 8/20=0.4, 4/20=0.2, 6/20=0.3 → avg 0.3
            assertThat(snapshot.heatScore()).isCloseTo(0.3, within(0.001));
        }

        @Test
        @DisplayName("empty cluster returns zero metrics")
        void emptyCluster() {
            var snapshot = AttentionSnapshot.ofCluster(List.of(), 20);

            assertThat(snapshot.heatScore()).isEqualTo(0.0);
            assertThat(snapshot.pressureScore()).isEqualTo(0.0);
            assertThat(snapshot.conflictCount()).isEqualTo(0);
            assertThat(snapshot.reinforcementCount()).isEqualTo(0);
            assertThat(snapshot.totalEventCount()).isEqualTo(0);
            assertThat(snapshot.unitIds()).isEmpty();
        }

        @Test
        @DisplayName("single-element cluster equals of() result")
        void singleElementClusterEqualsOf() {
            var window = new AttentionWindow("a1", "ctx", 2, 1, 6, NOW, NOW.minusSeconds(30), List.of(NOW));
            var fromOf = AttentionSnapshot.of(window, 20);
            var fromCluster = AttentionSnapshot.ofCluster(List.of(window), 20);

            assertThat(fromCluster.heatScore()).isCloseTo(fromOf.heatScore(), within(0.001));
            assertThat(fromCluster.pressureScore()).isCloseTo(fromOf.pressureScore(), within(0.001));
            assertThat(fromCluster.conflictCount()).isEqualTo(fromOf.conflictCount());
            assertThat(fromCluster.totalEventCount()).isEqualTo(fromOf.totalEventCount());
            assertThat(fromCluster.unitIds()).containsExactlyElementsOf(fromOf.unitIds());
        }

        @Test
        @DisplayName("pressure score is average of individual pressure scores")
        void pressureScoreIsAverage() {
            // w1: 4/8 = 0.5, w2: 0/4 = 0.0 → avg 0.25
            var w1 = new AttentionWindow("a1", "ctx", 4, 0, 8, NOW, NOW.minusSeconds(60), List.of(NOW));
            var w2 = new AttentionWindow("a2", "ctx", 0, 0, 4, NOW, NOW.minusSeconds(60), List.of(NOW));

            var snapshot = AttentionSnapshot.ofCluster(List.of(w1, w2), 20);

            assertThat(snapshot.pressureScore()).isCloseTo(0.25, within(0.001));
        }
    }
}
