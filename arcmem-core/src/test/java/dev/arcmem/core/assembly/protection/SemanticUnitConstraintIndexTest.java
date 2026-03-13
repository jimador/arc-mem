package dev.arcmem.core.assembly.protection;
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

@DisplayName("SemanticUnitConstraintIndex")
class SemanticUnitConstraintIndexTest {

    private static MemoryUnit unit(String id, String text, Authority authority) {
        return new MemoryUnit(id, text, 500, authority, false, 0.9, 0, null, 0.0, 1.0, MemoryTier.WARM, null);
    }

    @Nested
    @DisplayName("build")
    class Build {

        @Test
        @DisplayName("empty input produces empty index with zero coverage")
        void emptyInputProducesEmptyIndex() {
            var index = SemanticUnitConstraintIndex.build(List.of());
            assertThat(index.getConstraints()).isEmpty();
            assertThat(index.getTotalCoverage()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("null input produces empty index")
        void nullInputProducesEmptyIndex() {
            var index = SemanticUnitConstraintIndex.build(null);
            assertThat(index.getConstraints()).isEmpty();
        }

        @Test
        @DisplayName("only CANON and RELIABLE memory units produce constraints")
        void authorityFilteringExcludesLowerAuthority() {
            var canon = unit("c1", "Baron Krell leads the raiders", Authority.CANON);
            var reliable = unit("r1", "Captain Nyssa commands the fleet", Authority.RELIABLE);
            var provisional = unit("p1", "Something happened", Authority.PROVISIONAL);
            var unreliable = unit("u1", "The tower stands tall", Authority.UNRELIABLE);

            var index = SemanticUnitConstraintIndex.build(List.of(canon, reliable, provisional, unreliable));

            var ids = index.getConstraints().stream().map(SemanticUnitConstraint::unitId).toList();
            assertThat(ids).containsExactlyInAnyOrder("c1", "r1");
        }

        @Test
        @DisplayName("extracts capitalized entity names from memory unit text")
        void extractsEntityNamesFromText() {
            var unit = unit("a1", "Baron Krell is a four-armed sahuagin mutant", Authority.CANON);
            var index = SemanticUnitConstraintIndex.build(List.of(unit));

            assertThat(index.getConstraints()).hasSize(1);
            var constraint = index.getConstraints().get(0);
            assertThat(constraint.boostTokens()).contains("Baron", "Krell");
        }

        @Test
        @DisplayName("coverage is fraction of tokens that became constraints")
        void coverageCalculation() {
            // "Baron Krell is the leader" — 5 tokens, 2 entities (Baron, Krell)
            var unit = unit("a1", "Baron Krell is the leader", Authority.RELIABLE);
            var index = SemanticUnitConstraintIndex.build(List.of(unit));

            assertThat(index.getConstraints()).hasSize(1);
            var constraint = index.getConstraints().get(0);
            // 2 entity tokens out of 5 total
            assertThat(constraint.translationCoverage()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
        }

        @Test
        @DisplayName("memory unit with no entity names produces no constraint entry")
        void unitWithNoEntitiesProducesNoConstraint() {
            // all lowercase, no entities
            var unit = unit("a1", "the sky is blue and the sea is wide", Authority.CANON);
            var index = SemanticUnitConstraintIndex.build(List.of(unit));
            assertThat(index.getConstraints()).isEmpty();
        }
    }

    @Nested
    @DisplayName("isEntityCandidate")
    class IsEntityCandidate {

        @Test
        @DisplayName("capitalized proper noun passes")
        void capitalizedProperNounPasses() {
            assertThat(SemanticUnitConstraintIndex.isEntityCandidate("Baron")).isTrue();
            assertThat(SemanticUnitConstraintIndex.isEntityCandidate("Krell")).isTrue();
        }

        @Test
        @DisplayName("common stop words are excluded even when capitalized")
        void stopWordsExcluded() {
            assertThat(SemanticUnitConstraintIndex.isEntityCandidate("The")).isFalse();
            assertThat(SemanticUnitConstraintIndex.isEntityCandidate("Is")).isFalse();
        }

        @Test
        @DisplayName("lowercase words are excluded")
        void lowercaseExcluded() {
            assertThat(SemanticUnitConstraintIndex.isEntityCandidate("leader")).isFalse();
        }
    }
}
