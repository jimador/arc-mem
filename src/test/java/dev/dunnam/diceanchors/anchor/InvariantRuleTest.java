package dev.dunnam.diceanchors.anchor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InvariantRule — sealed subtypes")
class InvariantRuleTest {

    @Nested
    @DisplayName("AuthorityFloor")
    class AuthorityFloorTests {

        @Test
        @DisplayName("construction and field access")
        void constructionAndFieldAccess() {
            var rule = new InvariantRule.AuthorityFloor(
                    "af-1", InvariantStrength.MUST, "ctx-1", "safety", Authority.RELIABLE);

            assertThat(rule.id()).isEqualTo("af-1");
            assertThat(rule.strength()).isEqualTo(InvariantStrength.MUST);
            assertThat(rule.contextId()).isEqualTo("ctx-1");
            assertThat(rule.anchorTextPattern()).isEqualTo("safety");
            assertThat(rule.minimumAuthority()).isEqualTo(Authority.RELIABLE);
        }

        @Test
        @DisplayName("null contextId means global scope")
        void nullContextIdGlobalScope() {
            var rule = new InvariantRule.AuthorityFloor(
                    "af-global", InvariantStrength.SHOULD, null, "core", Authority.UNRELIABLE);

            assertThat(rule.contextId()).isNull();
        }
    }

    @Nested
    @DisplayName("EvictionImmunity")
    class EvictionImmunityTests {

        @Test
        @DisplayName("construction and field access")
        void constructionAndFieldAccess() {
            var rule = new InvariantRule.EvictionImmunity(
                    "ei-1", InvariantStrength.MUST, "ctx-2", "important");

            assertThat(rule.id()).isEqualTo("ei-1");
            assertThat(rule.strength()).isEqualTo(InvariantStrength.MUST);
            assertThat(rule.contextId()).isEqualTo("ctx-2");
            assertThat(rule.anchorTextPattern()).isEqualTo("important");
        }

        @Test
        @DisplayName("null contextId means global scope")
        void nullContextIdGlobalScope() {
            var rule = new InvariantRule.EvictionImmunity(
                    "ei-global", InvariantStrength.SHOULD, null, "core");

            assertThat(rule.contextId()).isNull();
        }
    }

    @Nested
    @DisplayName("MinAuthorityCount")
    class MinAuthorityCountTests {

        @Test
        @DisplayName("construction and field access")
        void constructionAndFieldAccess() {
            var rule = new InvariantRule.MinAuthorityCount(
                    "mac-1", InvariantStrength.MUST, "ctx-3", Authority.RELIABLE, 3);

            assertThat(rule.id()).isEqualTo("mac-1");
            assertThat(rule.strength()).isEqualTo(InvariantStrength.MUST);
            assertThat(rule.contextId()).isEqualTo("ctx-3");
            assertThat(rule.minimumAuthority()).isEqualTo(Authority.RELIABLE);
            assertThat(rule.minimumCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("null contextId means global scope")
        void nullContextIdGlobalScope() {
            var rule = new InvariantRule.MinAuthorityCount(
                    "mac-global", InvariantStrength.SHOULD, null, Authority.CANON, 1);

            assertThat(rule.contextId()).isNull();
        }
    }

    @Nested
    @DisplayName("ArchiveProhibition")
    class ArchiveProhibitionTests {

        @Test
        @DisplayName("construction and field access")
        void constructionAndFieldAccess() {
            var rule = new InvariantRule.ArchiveProhibition(
                    "ap-1", InvariantStrength.MUST, "ctx-4", "permanent");

            assertThat(rule.id()).isEqualTo("ap-1");
            assertThat(rule.strength()).isEqualTo(InvariantStrength.MUST);
            assertThat(rule.contextId()).isEqualTo("ctx-4");
            assertThat(rule.anchorTextPattern()).isEqualTo("permanent");
        }

        @Test
        @DisplayName("null contextId means global scope")
        void nullContextIdGlobalScope() {
            var rule = new InvariantRule.ArchiveProhibition(
                    "ap-global", InvariantStrength.SHOULD, null, "never-delete");

            assertThat(rule.contextId()).isNull();
        }
    }

    @Nested
    @DisplayName("pattern matching")
    class PatternMatching {

        @Test
        @DisplayName("switch expression matches all four sealed subtypes")
        void switchMatchesAllSubtypes() {
            var rules = java.util.List.<InvariantRule>of(
                    new InvariantRule.AuthorityFloor("af", InvariantStrength.MUST, null, "p", Authority.RELIABLE),
                    new InvariantRule.EvictionImmunity("ei", InvariantStrength.SHOULD, null, "p"),
                    new InvariantRule.MinAuthorityCount("mac", InvariantStrength.MUST, null, Authority.CANON, 1),
                    new InvariantRule.ArchiveProhibition("ap", InvariantStrength.SHOULD, null, "p")
            );

            var types = rules.stream()
                    .map(rule -> switch (rule) {
                        case InvariantRule.AuthorityFloor _ -> "floor";
                        case InvariantRule.EvictionImmunity _ -> "immunity";
                        case InvariantRule.MinAuthorityCount _ -> "count";
                        case InvariantRule.ArchiveProhibition _ -> "prohibition";
                    })
                    .toList();

            assertThat(types).containsExactly("floor", "immunity", "count", "prohibition");
        }

        @Test
        @DisplayName("instanceof checks confirm sealed hierarchy")
        void instanceofChecks() {
            InvariantRule rule = new InvariantRule.AuthorityFloor(
                    "af", InvariantStrength.MUST, null, "p", Authority.RELIABLE);

            assertThat(rule).isInstanceOf(InvariantRule.class);
            assertThat(rule).isInstanceOf(InvariantRule.AuthorityFloor.class);
        }
    }
}
