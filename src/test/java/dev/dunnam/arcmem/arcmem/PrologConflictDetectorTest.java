package dev.dunnam.diceanchors.anchor;

import com.embabel.dice.projection.prolog.PrologEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("PrologConflictDetector")
@ExtendWith(MockitoExtension.class)
class PrologConflictDetectorTest {

    private final AnchorPrologProjector realProjector = new AnchorPrologProjector();

    @Mock
    private AnchorPrologProjector mockProjector;

    private Anchor anchor(String id, String text) {
        return Anchor.withoutTrust(id, text, 500, Authority.RELIABLE, false, 0.9, 0);
    }

    @Nested
    @DisplayName("detect")
    class Detect {

        @Test
        @DisplayName("detects negation contradiction between incoming and existing anchor")
        void detectsNegationContradiction() {
            var detector = new PrologConflictDetector(realProjector);
            var existing = List.of(anchor("anc-001", "The guardian is alive"));
            var conflicts = detector.detect("The guardian is dead", existing);
            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.getFirst().existing().id()).isEqualTo("anc-001");
            assertThat(conflicts.getFirst().confidence()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("returns empty for non-contradicting anchors")
        void returnsEmptyForNonContradicting() {
            var detector = new PrologConflictDetector(realProjector);
            var existing = List.of(anchor("anc-001", "The tavern serves ale"));
            var conflicts = detector.detect("The king is on his throne", existing);
            assertThat(conflicts).isEmpty();
        }

        @Test
        @DisplayName("returns empty list on projector failure")
        void returnsEmptyOnProjectorFailure() {
            when(mockProjector.projectWithIncoming(anyList(), anyString()))
                    .thenThrow(new RuntimeException("Prolog engine failure"));
            var detector = new PrologConflictDetector(mockProjector);
            var existing = List.of(anchor("anc-001", "some anchor"));
            var conflicts = detector.detect("some incoming", existing);
            assertThat(conflicts).isEmpty();
        }

        @Test
        @DisplayName("returns empty for empty anchor list")
        void returnsEmptyForEmptyAnchors() {
            var detector = new PrologConflictDetector(realProjector);
            var conflicts = detector.detect("anything", List.of());
            assertThat(conflicts).isEmpty();
        }
    }
}
