package dev.dunnam.diceanchors.anchor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnchorPrologProjector")
class AnchorPrologProjectorTest {

    private final AnchorPrologProjector projector = new AnchorPrologProjector();

    private Anchor anchor(String id, String text, Authority authority) {
        return Anchor.withoutTrust(id, text, 500, authority, false, 0.9, 0);
    }

    @Nested
    @DisplayName("anchorFact")
    class AnchorFact {

        @Test
        @DisplayName("produces anchor/5 fact with correct arity")
        void producesAnchorFact() {
            var a = anchor("anc-001", "Baron Krell is alive", Authority.RELIABLE);
            var fact = projector.anchorFact(a);
            assertThat(fact).startsWith("anchor('anc-001', 2, 500, false, 0).");
        }

        @Test
        @DisplayName("CANON authority maps to ordinal 3")
        void canonMapsToOrdinal3() {
            var a = anchor("anc-002", "text", Authority.CANON);
            var fact = projector.anchorFact(a);
            assertThat(fact).contains(", 3,");
        }

        @Test
        @DisplayName("PROVISIONAL authority maps to ordinal 0")
        void provisionalMapsToOrdinal0() {
            var a = anchor("anc-003", "text", Authority.PROVISIONAL);
            var fact = projector.anchorFact(a);
            assertThat(fact).contains(", 0,");
        }
    }

    @Nested
    @DisplayName("decomposeText")
    class DecomposeText {

        @Test
        @DisplayName("produces claim/4 facts for simple 'X is Y' sentence")
        void decomposeIsPattern() {
            var claims = projector.decomposeText("a1", "Baron Krell is alive");
            assertThat(claims).isNotEmpty();
            assertThat(claims.getFirst()).contains("claim(");
            assertThat(claims.getFirst()).contains("'a1'");
        }

        @Test
        @DisplayName("fallback for very short text produces unknown subject claim")
        void fallbackForShortText() {
            var claims = projector.decomposeText("a1", "x");
            assertThat(claims).hasSize(1);
            assertThat(claims.getFirst()).contains("'unknown'");
            assertThat(claims.getFirst()).contains("'states'");
        }

        @Test
        @DisplayName("empty text returns empty list")
        void emptyTextReturnsEmpty() {
            var claims = projector.decomposeText("a1", "");
            assertThat(claims).isEmpty();
        }

        @Test
        @DisplayName("multi-sentence text produces multiple claims")
        void multiSentenceProducesMultipleClaims() {
            var claims = projector.decomposeText("a1", "The king is alive. The queen is present");
            assertThat(claims).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("project")
    class Project {

        @Test
        @DisplayName("empty anchor list produces valid engine")
        void emptyAnchorListProducesValidEngine() {
            var engine = projector.project(List.of());
            assertThat(engine).isNotNull();
            assertThat(engine.query("true")).isTrue();
        }

        @Test
        @DisplayName("anchors are projected to queryable facts")
        void anchorsProjectedToFacts() {
            var anchors = List.of(anchor("anc-001", "Baron Krell is alive", Authority.RELIABLE));
            var engine = projector.project(anchors);
            assertThat(engine).isNotNull();
            assertThat(engine.query("anchor('anc-001', _, _, _, _)")).isTrue();
        }
    }

    @Nested
    @DisplayName("projectWithIncoming")
    class ProjectWithIncoming {

        @Test
        @DisplayName("adds synthetic incoming anchor queryable by anchor/5")
        void addsIncomingFact() {
            var anchors = List.of(anchor("anc-001", "Baron Krell is alive", Authority.RELIABLE));
            var engine = projector.projectWithIncoming(anchors, "Baron Krell is dead");
            assertThat(engine).isNotNull();
            assertThat(engine.query("anchor('anc-001', _, _, _, _)")).isTrue();
        }

        @Test
        @DisplayName("detects negation contradiction between incoming and existing via rules")
        void detectsNegationContradiction() {
            var anchors = List.of(anchor("anc-001", "The guardian is alive", Authority.RELIABLE));
            var engine = projector.projectWithIncoming(anchors, "The guardian is dead");
            assertThat(engine.query("conflicts_with_incoming('anc-001')")).isTrue();
        }
    }
}
