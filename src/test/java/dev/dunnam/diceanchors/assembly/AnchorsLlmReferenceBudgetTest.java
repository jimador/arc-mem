package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.DefaultCompliancePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AnchorsLlmReference token budget integration")
class AnchorsLlmReferenceBudgetTest {

    private static final String CONTEXT_ID = "ctx";

    @Test
    @DisplayName("budget zero preserves existing behavior")
    void getContentBudgetZeroMatchesBaselineBehavior() {
        var engine = mock(AnchorEngine.class);
        var anchors = List.of(
                anchor("a1", "Reliable fact", Authority.RELIABLE),
                anchor("a2", "Provisional fact", Authority.PROVISIONAL));
        when(engine.inject(CONTEXT_ID)).thenReturn(anchors);

        var baseline = new AnchorsLlmReference(engine, CONTEXT_ID, 20, new DefaultCompliancePolicy());
        var budgetDisabled = new AnchorsLlmReference(engine, CONTEXT_ID, 20, new DefaultCompliancePolicy(), 0, new CharHeuristicTokenCounter());

        assertThat(budgetDisabled.getContent()).isEqualTo(baseline.getContent());
        assertThat(budgetDisabled.getAnchors()).containsExactlyElementsOf(baseline.getAnchors());
    }

    @Test
    @DisplayName("token budget truncates lower authority anchors")
    void getContentWithBudgetTruncatesLowerAuthorityAnchors() {
        var engine = mock(AnchorEngine.class);
        var canon = anchor("c1", "Canon fact", Authority.CANON);
        var reliable = anchor("r1", "Reliable fact", Authority.RELIABLE);
        var provisional = anchor("p1", "Provisional fact", Authority.PROVISIONAL);
        when(engine.inject(CONTEXT_ID)).thenReturn(List.of(canon, reliable, provisional));

        var counter = (TokenCounter) text -> {
            if (text == null || text.isBlank()) {
                return 0;
            }
            if (text.startsWith("[Established Facts")) {
                return 10;
            }
            if (text.startsWith(" (rank:")) {
                return 1;
            }
            return 10;
        };

        var ref = new AnchorsLlmReference(engine, CONTEXT_ID, 20, new DefaultCompliancePolicy(), 32, counter);
        var content = ref.getContent();

        assertThat(ref.getAnchors()).contains(canon, reliable).doesNotContain(provisional);
        assertThat(ref.getLastBudgetResult().excluded()).containsExactly(provisional);
        assertThat(content).contains("CANON FACTS").contains("RELIABLE FACTS");
        assertThat(content).doesNotContain("Provisional fact");
    }

    private static Anchor anchor(String id, String text, Authority authority) {
        return Anchor.withoutTrust(id, text, 500, authority, false, 0.9, 0);
    }
}
