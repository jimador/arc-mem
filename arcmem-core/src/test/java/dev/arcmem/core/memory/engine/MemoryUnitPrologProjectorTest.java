package dev.arcmem.core.memory.engine;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MemoryUnitPrologProjector")
class MemoryUnitPrologProjectorTest {

    private final MemoryUnitPrologProjector projector = new MemoryUnitPrologProjector();

    private MemoryUnit unit(String id, String text, Authority authority) {
        return MemoryUnit.withoutTrust(id, text, 500, authority, false, 0.9, 0);
    }

    @Nested
    @DisplayName("contextUnitFact")
    class UnitFact {

        @Test
        @DisplayName("produces memory unit fact with correct arity")
        void producesUnitFact() {
            var a = unit("anc-001", "Baron Krell is alive", Authority.RELIABLE);
            var fact = projector.unitFact(a);
            assertThat(fact).startsWith("unit('anc-001', 2, 500, false, 0).");
        }

        @Test
        @DisplayName("CANON authority maps to ordinal 3")
        void canonMapsToOrdinal3() {
            var a = unit("anc-002", "text", Authority.CANON);
            var fact = projector.unitFact(a);
            assertThat(fact).contains(", 3,");
        }

        @Test
        @DisplayName("PROVISIONAL authority maps to ordinal 0")
        void provisionalMapsToOrdinal0() {
            var a = unit("anc-003", "text", Authority.PROVISIONAL);
            var fact = projector.unitFact(a);
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
        @DisplayName("empty memory unit list produces valid engine")
        void emptyUnitListProducesValidEngine() {
            var engine = projector.project(List.of());
            assertThat(engine).isNotNull();
            assertThat(engine.query("true")).isTrue();
        }

        @Test
        @DisplayName("memory units are projected to queryable facts")
        void unitsProjectedToFacts() {
            var units = List.of(unit("anc-001", "Baron Krell is alive", Authority.RELIABLE));
            var engine = projector.project(units);
            assertThat(engine).isNotNull();
            assertThat(engine.query("unit('anc-001', _, _, _, _)")).isTrue();
        }
    }

    @Nested
    @DisplayName("projectWithIncoming")
    class ProjectWithIncoming {

        @Test
        @DisplayName("adds synthetic incoming memory unit as queryable fact")
        void addsIncomingFact() {
            var units = List.of(unit("anc-001", "Baron Krell is alive", Authority.RELIABLE));
            var engine = projector.projectWithIncoming(units, "Baron Krell is dead");
            assertThat(engine).isNotNull();
            assertThat(engine.query("unit('anc-001', _, _, _, _)")).isTrue();
        }

        @Test
        @DisplayName("detects negation contradiction between incoming and existing via rules")
        void detectsNegationContradiction() {
            var units = List.of(unit("anc-001", "The guardian is alive", Authority.RELIABLE));
            var engine = projector.projectWithIncoming(units, "The guardian is dead");
            assertThat(engine.query("conflicts_with_incoming('anc-001')")).isTrue();
        }
    }
}
