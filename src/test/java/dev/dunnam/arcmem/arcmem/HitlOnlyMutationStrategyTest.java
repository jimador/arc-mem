package dev.dunnam.diceanchors.anchor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HitlOnlyMutationStrategyTest {

    private final HitlOnlyMutationStrategy strategy = new HitlOnlyMutationStrategy();

    @Test
    @DisplayName("uiSourceReturnsAllow")
    void uiSourceReturnsAllow() {
        var request = new MutationRequest("anchor-1", "revised text", MutationSource.UI, "operator");
        var decision = strategy.evaluate(request);
        assertThat(decision).isInstanceOf(MutationDecision.Allow.class);
    }

    @Test
    @DisplayName("llmToolSourceReturnsDeny")
    void llmToolSourceReturnsDeny() {
        var request = new MutationRequest("anchor-1", "revised text", MutationSource.LLM_TOOL, "llm");
        var decision = strategy.evaluate(request);
        assertThat(decision).isInstanceOf(MutationDecision.Deny.class);
        assertThat(((MutationDecision.Deny) decision).reason()).contains("HITL-only");
    }

    @Test
    @DisplayName("conflictResolverSourceReturnsDeny")
    void conflictResolverSourceReturnsDeny() {
        var request = new MutationRequest("anchor-1", "revised text", MutationSource.CONFLICT_RESOLVER, "resolver");
        var decision = strategy.evaluate(request);
        assertThat(decision).isInstanceOf(MutationDecision.Deny.class);
        assertThat(((MutationDecision.Deny) decision).reason()).contains("HITL-only");
    }

    @Test
    @DisplayName("denialReasonsAreDistinct")
    void denialReasonsAreDistinct() {
        var llmRequest = new MutationRequest("a", "t", MutationSource.LLM_TOOL, "llm");
        var crRequest = new MutationRequest("a", "t", MutationSource.CONFLICT_RESOLVER, "cr");

        var llmDeny = (MutationDecision.Deny) strategy.evaluate(llmRequest);
        var crDeny = (MutationDecision.Deny) strategy.evaluate(crRequest);

        assertThat(llmDeny.reason()).contains("LLM-initiated");
        assertThat(crDeny.reason()).contains("Conflict-resolver");
    }
}
