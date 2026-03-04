package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorPrologProjector;
import dev.dunnam.diceanchors.anchor.Authority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("PrologInvariantEnforcer")
@ExtendWith(MockitoExtension.class)
class PrologInvariantEnforcerTest {

    private final AnchorPrologProjector realProjector = new AnchorPrologProjector();

    @Mock
    private AnchorPrologProjector mockProjector;

    private Anchor anchor(String id, String text, Authority authority) {
        return Anchor.withoutTrust(id, text, 500, authority, false, 0.9, 0);
    }

    private ComplianceContext context(String response, List<Anchor> anchors,
                                      ComplianceContext.CompliancePolicy policy) {
        return new ComplianceContext(response, anchors, policy);
    }

    @Nested
    @DisplayName("enforce")
    class Enforce {

        @Test
        @DisplayName("detects contradiction with CANON anchor and returns REJECT")
        void detectsCanonContradictionAndRejects() {
            var enforcer = new PrologInvariantEnforcer(realProjector);
            var anchors = List.of(anchor("anc-001", "The guardian is alive", Authority.CANON));
            var ctx = context("The guardian is dead", anchors,
                    ComplianceContext.CompliancePolicy.canonOnly());

            var result = enforcer.enforce(ctx);

            assertThat(result.compliant()).isFalse();
            assertThat(result.suggestedAction()).isEqualTo(ComplianceAction.REJECT);
            assertThat(result.violations()).hasSize(1);
            assertThat(result.violations().getFirst().anchorId()).isEqualTo("anc-001");
        }

        @Test
        @DisplayName("returns compliant ACCEPT for non-contradicting response")
        void returnsCompliantForNonContradicting() {
            var enforcer = new PrologInvariantEnforcer(realProjector);
            var anchors = List.of(anchor("anc-001", "The tavern serves ale", Authority.RELIABLE));
            var ctx = context("The king sits on his throne", anchors,
                    ComplianceContext.CompliancePolicy.tiered());

            var result = enforcer.enforce(ctx);

            assertThat(result.compliant()).isTrue();
            assertThat(result.suggestedAction()).isEqualTo(ComplianceAction.ACCEPT);
        }

        @Test
        @DisplayName("respects CompliancePolicy filter -- canonOnly excludes PROVISIONAL")
        void respectsPolicyFilter() {
            var enforcer = new PrologInvariantEnforcer(realProjector);
            var anchors = List.of(anchor("anc-001", "The guardian is alive", Authority.PROVISIONAL));
            var ctx = context("The guardian is dead", anchors,
                    ComplianceContext.CompliancePolicy.canonOnly());

            var result = enforcer.enforce(ctx);

            assertThat(result.compliant()).isTrue();
            assertThat(result.suggestedAction()).isEqualTo(ComplianceAction.ACCEPT);
        }

        @Test
        @DisplayName("returns compliant on projector failure (fail-open)")
        void returnsCompliantOnFailure() {
            when(mockProjector.projectWithIncoming(anyList(), anyString()))
                    .thenThrow(new RuntimeException("Prolog engine error"));
            var enforcer = new PrologInvariantEnforcer(mockProjector);
            var anchors = List.of(anchor("anc-001", "The guardian is alive", Authority.CANON));
            var ctx = context("The guardian is dead", anchors,
                    ComplianceContext.CompliancePolicy.canonOnly());

            var result = enforcer.enforce(ctx);

            assertThat(result.compliant()).isTrue();
            assertThat(result.suggestedAction()).isEqualTo(ComplianceAction.ACCEPT);
        }
    }
}
