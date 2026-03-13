package dev.arcmem.core.assembly.retrieval;
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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ArcMemLlmReference token budget integration")
class ArcMemLlmReferenceBudgetTest {

    private static final String CONTEXT_ID = "ctx";

    @Test
    @DisplayName("budget zero preserves existing behavior")
    void getContentBudgetZeroMatchesBaselineBehavior() {
        var engine = mock(ArcMemEngine.class);
        var units = List.of(
                unit("a1", "Reliable fact", Authority.RELIABLE),
                unit("a2", "Provisional fact", Authority.PROVISIONAL));
        when(engine.inject(CONTEXT_ID)).thenReturn(units);

        var baseline = new ArcMemLlmReference(engine, CONTEXT_ID, 20, CompliancePolicy.flat());
        var budgetDisabled = new ArcMemLlmReference(engine, CONTEXT_ID, 20, CompliancePolicy.flat(), 0, new CharHeuristicTokenCounter());

        assertThat(budgetDisabled.getContent()).isEqualTo(baseline.getContent());
        assertThat(budgetDisabled.getUnits()).containsExactlyElementsOf(baseline.getUnits());
    }

    @Test
    @DisplayName("token budget truncates lower authority memory units")
    void getContentWithBudgetTruncatesLowerAuthorityUnits() {
        var engine = mock(ArcMemEngine.class);
        var canon = unit("c1", "Canon fact", Authority.CANON);
        var reliable = unit("r1", "Reliable fact", Authority.RELIABLE);
        var provisional = unit("p1", "Provisional fact", Authority.PROVISIONAL);
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

        var ref = new ArcMemLlmReference(engine, CONTEXT_ID, 20, CompliancePolicy.flat(), 32, counter);
        var content = ref.getContent();

        assertThat(ref.getUnits()).contains(canon, reliable).doesNotContain(provisional);
        assertThat(ref.getLastBudgetResult().excluded()).containsExactly(provisional);
        assertThat(content).contains("CANON FACTS").contains("RELIABLE FACTS");
        assertThat(content).doesNotContain("Provisional fact");
    }

    private static MemoryUnit unit(String id, String text, Authority authority) {
        return MemoryUnit.withoutTrust(id, text, 500, authority, false, 0.9, 0);
    }
}
