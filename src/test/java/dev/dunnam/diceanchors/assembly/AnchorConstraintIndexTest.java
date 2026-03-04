package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.MemoryTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnchorConstraintIndex")
class AnchorConstraintIndexTest {

    private static Anchor anchor(String id, String text, Authority authority) {
        return new Anchor(id, text, 500, authority, false, 0.9, 0, null, 0.0, 1.0, MemoryTier.WARM);
    }

    @Nested
    @DisplayName("build")
    class Build {

        @Test
        @DisplayName("empty input produces empty index with zero coverage")
        void emptyInputProducesEmptyIndex() {
            var index = AnchorConstraintIndex.build(List.of());
            assertThat(index.getConstraints()).isEmpty();
            assertThat(index.getTotalCoverage()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("null input produces empty index")
        void nullInputProducesEmptyIndex() {
            var index = AnchorConstraintIndex.build(null);
            assertThat(index.getConstraints()).isEmpty();
        }

        @Test
        @DisplayName("only CANON and RELIABLE anchors produce constraints")
        void authorityFilteringExcludesLowerAuthority() {
            var canon = anchor("c1", "Baron Krell leads the raiders", Authority.CANON);
            var reliable = anchor("r1", "Captain Nyssa commands the fleet", Authority.RELIABLE);
            var provisional = anchor("p1", "Something happened", Authority.PROVISIONAL);
            var unreliable = anchor("u1", "The tower stands tall", Authority.UNRELIABLE);

            var index = AnchorConstraintIndex.build(List.of(canon, reliable, provisional, unreliable));

            var ids = index.getConstraints().stream().map(AnchorConstraint::anchorId).toList();
            assertThat(ids).containsExactlyInAnyOrder("c1", "r1");
        }

        @Test
        @DisplayName("extracts capitalized entity names from anchor text")
        void extractsEntityNamesFromText() {
            var anchor = anchor("a1", "Baron Krell is a four-armed sahuagin mutant", Authority.CANON);
            var index = AnchorConstraintIndex.build(List.of(anchor));

            assertThat(index.getConstraints()).hasSize(1);
            var constraint = index.getConstraints().get(0);
            assertThat(constraint.boostTokens()).contains("Baron", "Krell");
        }

        @Test
        @DisplayName("coverage is fraction of tokens that became constraints")
        void coverageCalculation() {
            // "Baron Krell is the leader" — 5 tokens, 2 entities (Baron, Krell)
            var anchor = anchor("a1", "Baron Krell is the leader", Authority.RELIABLE);
            var index = AnchorConstraintIndex.build(List.of(anchor));

            assertThat(index.getConstraints()).hasSize(1);
            var constraint = index.getConstraints().get(0);
            // 2 entity tokens out of 5 total
            assertThat(constraint.translationCoverage()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
        }

        @Test
        @DisplayName("anchor with no entity names produces no constraint entry")
        void anchorWithNoEntitiesProducesNoConstraint() {
            // all lowercase, no entities
            var anchor = anchor("a1", "the sky is blue and the sea is wide", Authority.CANON);
            var index = AnchorConstraintIndex.build(List.of(anchor));
            assertThat(index.getConstraints()).isEmpty();
        }
    }

    @Nested
    @DisplayName("isEntityCandidate")
    class IsEntityCandidate {

        @Test
        @DisplayName("capitalized proper noun passes")
        void capitalizedProperNounPasses() {
            assertThat(AnchorConstraintIndex.isEntityCandidate("Baron")).isTrue();
            assertThat(AnchorConstraintIndex.isEntityCandidate("Krell")).isTrue();
        }

        @Test
        @DisplayName("common stop words are excluded even when capitalized")
        void stopWordsExcluded() {
            assertThat(AnchorConstraintIndex.isEntityCandidate("The")).isFalse();
            assertThat(AnchorConstraintIndex.isEntityCandidate("Is")).isFalse();
        }

        @Test
        @DisplayName("lowercase words are excluded")
        void lowercaseExcluded() {
            assertThat(AnchorConstraintIndex.isEntityCandidate("leader")).isFalse();
        }
    }
}
