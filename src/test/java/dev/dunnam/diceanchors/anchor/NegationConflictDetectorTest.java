package dev.dunnam.diceanchors.anchor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NegationConflictDetector")
class NegationConflictDetectorTest {

    private final NegationConflictDetector detector = new NegationConflictDetector();

    @Nested
    @DisplayName("detect")
    class Detect {

        @Test
        @DisplayName("detects negation conflict between positive and negative statements")
        void detectsNegationConflict() {
            var anchors = List.of(
                    Anchor.withoutTrust("1", "The king is alive and well", 700, Authority.RELIABLE, false, 0.9, 0)
            );
            var conflicts = detector.detect("The king is not alive", anchors);
            assertThat(conflicts).isNotEmpty();
            assertThat(conflicts.getFirst().existing().id()).isEqualTo("1");
        }

        @Test
        @DisplayName("no conflict for unrelated statements")
        void noConflictForUnrelatedStatements() {
            var anchors = List.of(
                    Anchor.withoutTrust("1", "The sword glows blue", 500, Authority.PROVISIONAL, false, 0.8, 0)
            );
            var conflicts = detector.detect("The tavern serves good ale", anchors);
            assertThat(conflicts).isEmpty();
        }

        @Test
        @DisplayName("no conflict when both statements have same polarity")
        void noConflictSamePolarity() {
            var anchors = List.of(
                    Anchor.withoutTrust("1", "The dragon is not friendly", 600, Authority.RELIABLE, false, 0.9, 0)
            );
            var conflicts = detector.detect("The dragon is not tame", anchors);
            assertThat(conflicts).isEmpty();
        }

        @Test
        @DisplayName("empty anchors produces no conflicts")
        void emptyAnchorsNoConflicts() {
            var conflicts = detector.detect("Any statement", List.of());
            assertThat(conflicts).isEmpty();
        }
    }
}
