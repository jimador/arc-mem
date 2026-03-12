package dev.dunnam.diceanchors.anchor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@DisplayName("PrologAuditPreFilter")
@ExtendWith(MockitoExtension.class)
class PrologAuditPreFilterTest {

    private final AnchorPrologProjector realProjector = new AnchorPrologProjector();

    @Mock
    private AnchorPrologProjector mockProjector;

    private Anchor anchor(String id, String text) {
        return Anchor.withoutTrust(id, text, 500, Authority.RELIABLE, false, 0.9, 0);
    }

    @Nested
    @DisplayName("flagContradictingAnchors")
    class FlagContradictingAnchors {

        @Test
        @DisplayName("flags both anchors in a contradicting pair")
        void flagsContradictingPair() {
            var filter = new PrologAuditPreFilter(realProjector);
            var anchors = List.of(
                    anchor("anc-001", "The guardian is alive"),
                    anchor("anc-002", "The guardian is dead")
            );
            var flagged = filter.flagContradictingAnchors(anchors);
            assertThat(flagged).contains("anc-001", "anc-002");
        }

        @Test
        @DisplayName("returns empty set when no contradictions")
        void returnsEmptySetForNonContradicting() {
            var filter = new PrologAuditPreFilter(realProjector);
            var anchors = List.of(
                    anchor("anc-001", "The tavern serves ale"),
                    anchor("anc-002", "The king sits on his throne")
            );
            var flagged = filter.flagContradictingAnchors(anchors);
            assertThat(flagged).isEmpty();
        }

        @Test
        @DisplayName("returns empty set on projector failure")
        void returnsEmptySetOnFailure() {
            when(mockProjector.project(anyList()))
                    .thenThrow(new RuntimeException("Prolog failure"));
            var filter = new PrologAuditPreFilter(mockProjector);
            var anchors = List.of(anchor("anc-001", "some anchor"));
            var flagged = filter.flagContradictingAnchors(anchors);
            assertThat(flagged).isEmpty();
        }

        @Test
        @DisplayName("returns empty set for empty anchor list")
        void returnsEmptySetForEmptyAnchors() {
            var filter = new PrologAuditPreFilter(realProjector);
            var flagged = filter.flagContradictingAnchors(List.of());
            assertThat(flagged).isEmpty();
        }
    }
}
