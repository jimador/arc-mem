package dev.arcmem.core.assembly.budget;
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
    @DisplayName("includes all memory units when budget is not exceeded")
    void budgetNotExceededIncludesAllUnits() {
        var units = List.of(
                unit("a1", Authority.RELIABLE),
                unit("a2", Authority.UNRELIABLE));

        var result = enforcer.enforce(units, 40, counter, CompliancePolicy.flat());

        assertThat(result.included()).hasSize(2);
        assertThat(result.excluded()).isEmpty();
        assertThat(result.budgetExceeded()).isFalse();
    }

    @Test
    @DisplayName("drops PROVISIONAL before higher authority memory units")
    void dropsProvisionalFirstWhenOverBudget() {
        var provisional = unit("p1", Authority.PROVISIONAL);
        var reliable = unit("r1", Authority.RELIABLE);
        var canon = unit("c1", Authority.CANON);

        var result = enforcer.enforce(
                List.of(canon, reliable, provisional),
                32,
                counter,
                CompliancePolicy.flat());

        assertThat(result.included()).contains(canon, reliable);
        assertThat(result.excluded()).containsExactly(provisional);
    }

    @Test
    @DisplayName("never drops CANON memory units even when budget is exceeded")
    void canonNeverDroppedWhenOverBudget() {
        var canonA = unit("c1", Authority.CANON);
        var canonB = unit("c2", Authority.CANON);

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
    @DisplayName("handles empty memory unit list")
    void handlesEmptyUnitList() {
        var result = enforcer.enforce(List.of(), 20, counter, CompliancePolicy.flat());

        assertThat(result.included()).isEmpty();
        assertThat(result.excluded()).isEmpty();
    }

    @Test
    @DisplayName("budget zero returns all memory units unchanged")
    void budgetZeroReturnsAllUnits() {
        var units = List.of(unit("a1", Authority.PROVISIONAL), unit("a2", Authority.RELIABLE));

        var result = enforcer.enforce(units, 0, counter, CompliancePolicy.flat());

        assertThat(result.included()).containsExactlyElementsOf(units);
        assertThat(result.excluded()).isEmpty();
    }

    private static MemoryUnit unit(String id, Authority authority) {
        return MemoryUnit.withoutTrust(id, id + " text", 500, authority, false, 0.9, 0);
    }
}
