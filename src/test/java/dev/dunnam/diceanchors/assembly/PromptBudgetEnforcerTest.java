package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.CompliancePolicy;
import dev.dunnam.diceanchors.anchor.Authority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PromptBudgetEnforcer")
class PromptBudgetEnforcerTest {

    private final PromptBudgetEnforcer enforcer = new PromptBudgetEnforcer();
    private final TokenCounter counter = text -> {
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

    @Test
    @DisplayName("includes all anchors when budget is not exceeded")
    void budgetNotExceededIncludesAllAnchors() {
        var anchors = List.of(
                anchor("a1", Authority.RELIABLE),
                anchor("a2", Authority.UNRELIABLE));

        var result = enforcer.enforce(anchors, 40, counter, CompliancePolicy.flat());

        assertThat(result.included()).hasSize(2);
        assertThat(result.excluded()).isEmpty();
        assertThat(result.budgetExceeded()).isFalse();
    }

    @Test
    @DisplayName("drops PROVISIONAL before higher authority anchors")
    void dropsProvisionalFirstWhenOverBudget() {
        var provisional = anchor("p1", Authority.PROVISIONAL);
        var reliable = anchor("r1", Authority.RELIABLE);
        var canon = anchor("c1", Authority.CANON);

        var result = enforcer.enforce(
                List.of(canon, reliable, provisional),
                32,
                counter,
                CompliancePolicy.flat());

        assertThat(result.included()).contains(canon, reliable);
        assertThat(result.excluded()).containsExactly(provisional);
    }

    @Test
    @DisplayName("never drops CANON anchors even when budget is exceeded")
    void canonNeverDroppedWhenOverBudget() {
        var canonA = anchor("c1", Authority.CANON);
        var canonB = anchor("c2", Authority.CANON);

        var result = enforcer.enforce(
                List.of(canonA, canonB),
                5,
                counter,
                CompliancePolicy.flat());

        assertThat(result.included()).containsExactly(canonA, canonB);
        assertThat(result.excluded()).isEmpty();
        assertThat(result.budgetExceeded()).isTrue();
    }

    @Test
    @DisplayName("handles empty anchor list")
    void handlesEmptyAnchorList() {
        var result = enforcer.enforce(List.of(), 20, counter, CompliancePolicy.flat());

        assertThat(result.included()).isEmpty();
        assertThat(result.excluded()).isEmpty();
    }

    @Test
    @DisplayName("budget zero returns all anchors unchanged")
    void budgetZeroReturnsAllAnchors() {
        var anchors = List.of(anchor("a1", Authority.PROVISIONAL), anchor("a2", Authority.RELIABLE));

        var result = enforcer.enforce(anchors, 0, counter, CompliancePolicy.flat());

        assertThat(result.included()).containsExactlyElementsOf(anchors);
        assertThat(result.excluded()).isEmpty();
    }

    private static Anchor anchor(String id, Authority authority) {
        return Anchor.withoutTrust(id, id + " text", 500, authority, false, 0.9, 0);
    }
}
