package dev.dunnam.diceanchors.anchor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthorityConflictResolver")
class AuthorityConflictResolverTest {

    private final ConflictResolver resolver = new AuthorityConflictResolver();

    // Legacy tests (byAuthority() lambda) kept for contract coverage
    private final ConflictResolver legacyResolver = ConflictResolver.byAuthority();

    @Test
    @DisplayName("keeps existing when anchor has RELIABLE authority (legacy resolver)")
    void keepsExistingReliable() {
        var conflict = new ConflictDetector.Conflict(
                Anchor.withoutTrust("1", "King is dead", 700, Authority.RELIABLE, false, 0.9, 0),
                "King is alive", 0.7, "negation"
        );
        assertThat(legacyResolver.resolve(conflict)).isEqualTo(ConflictResolver.Resolution.KEEP_EXISTING);
    }

    @Test
    @DisplayName("keeps existing when anchor has CANON authority (legacy resolver)")
    void keepsExistingCanon() {
        var conflict = new ConflictDetector.Conflict(
                Anchor.withoutTrust("1", "King is dead", 850, Authority.CANON, true, 0.99, 5),
                "King is alive", 0.9, "negation"
        );
        assertThat(legacyResolver.resolve(conflict)).isEqualTo(ConflictResolver.Resolution.KEEP_EXISTING);
    }

    @Test
    @DisplayName("replaces PROVISIONAL anchor with high-confidence incoming (legacy resolver)")
    void replacesProvisionalHighConfidence() {
        var conflict = new ConflictDetector.Conflict(
                Anchor.withoutTrust("1", "Something uncertain", 300, Authority.PROVISIONAL, false, 0.5, 0),
                "Something more certain", 0.9, "contradiction"
        );
        assertThat(legacyResolver.resolve(conflict)).isEqualTo(ConflictResolver.Resolution.REPLACE);
    }

    @Test
    @DisplayName("coexists for PROVISIONAL anchor with moderate confidence (legacy resolver)")
    void coexistsProvisionalModerateConfidence() {
        var conflict = new ConflictDetector.Conflict(
                Anchor.withoutTrust("1", "Maybe this", 400, Authority.PROVISIONAL, false, 0.6, 0),
                "Maybe that", 0.6, "partial overlap"
        );
        assertThat(legacyResolver.resolve(conflict)).isEqualTo(ConflictResolver.Resolution.COEXIST);
    }

    @Nested
    @DisplayName("CANON always KEEP_EXISTING")
    class CanonAlwaysKeep {

        @Test
        @DisplayName("CANON returns KEEP_EXISTING regardless of incoming confidence")
        void canonAlwaysKeepsExisting() {
            var conflict = new ConflictDetector.Conflict(
                    Anchor.withoutTrust("1", "Canon fact", 850, Authority.CANON, true, 0.99, 5),
                    "Contradicting text", 0.99, "negation"
            );
            assertThat(resolver.resolve(conflict)).isEqualTo(ConflictResolver.Resolution.KEEP_EXISTING);
        }
    }

    @Nested
    @DisplayName("RELIABLE authority matrix")
    class ReliableMatrix {

        @Test
        @DisplayName("RELIABLE + high confidence (0.85) returns REPLACE")
        void reliableHighConfidenceReturnsReplace() {
            var conflict = new ConflictDetector.Conflict(
                    Anchor.withoutTrust("1", "Reliable fact", 700, Authority.RELIABLE, false, 0.9, 3),
                    "High-confidence counter", 0.85, "negation"
            );
            assertThat(resolver.resolve(conflict)).isEqualTo(ConflictResolver.Resolution.REPLACE);
        }

        @Test
        @DisplayName("RELIABLE + mid confidence (0.70) returns DEMOTE_EXISTING")
        void reliableMidConfidenceDemotesExisting() {
            var conflict = new ConflictDetector.Conflict(
                    Anchor.withoutTrust("1", "Reliable fact", 700, Authority.RELIABLE, false, 0.9, 3),
                    "Mid-confidence counter", 0.70, "negation"
            );
            assertThat(resolver.resolve(conflict)).isEqualTo(ConflictResolver.Resolution.DEMOTE_EXISTING);
        }

        @Test
        @DisplayName("RELIABLE + low confidence (0.50) returns KEEP_EXISTING")
        void reliableLowConfidenceKeepsExisting() {
            var conflict = new ConflictDetector.Conflict(
                    Anchor.withoutTrust("1", "Reliable fact", 700, Authority.RELIABLE, false, 0.9, 3),
                    "Low-confidence counter", 0.50, "negation"
            );
            assertThat(resolver.resolve(conflict)).isEqualTo(ConflictResolver.Resolution.KEEP_EXISTING);
        }
    }

    @Nested
    @DisplayName("UNRELIABLE authority matrix")
    class UnreliableMatrix {

        @Test
        @DisplayName("UNRELIABLE + adequate confidence (0.65) returns REPLACE")
        void unreliableAdequateConfidenceReturnsReplace() {
            var conflict = new ConflictDetector.Conflict(
                    Anchor.withoutTrust("1", "Unreliable fact", 350, Authority.UNRELIABLE, false, 0.6, 1),
                    "Adequate counter", 0.65, "negation"
            );
            assertThat(resolver.resolve(conflict)).isEqualTo(ConflictResolver.Resolution.REPLACE);
        }

        @Test
        @DisplayName("UNRELIABLE + low confidence (0.40) returns DEMOTE_EXISTING")
        void unreliableLowConfidenceDemotesExisting() {
            var conflict = new ConflictDetector.Conflict(
                    Anchor.withoutTrust("1", "Unreliable fact", 350, Authority.UNRELIABLE, false, 0.6, 1),
                    "Low counter", 0.40, "negation"
            );
            assertThat(resolver.resolve(conflict)).isEqualTo(ConflictResolver.Resolution.DEMOTE_EXISTING);
        }
    }

    @Nested
    @DisplayName("PROVISIONAL always REPLACE")
    class ProvisionalAlwaysReplace {

        @Test
        @DisplayName("PROVISIONAL returns REPLACE at any confidence")
        void provisionalAlwaysReplaces() {
            var conflict = new ConflictDetector.Conflict(
                    Anchor.withoutTrust("1", "Provisional fact", 300, Authority.PROVISIONAL, false, 0.5, 0),
                    "Any counter", 0.3, "negation"
            );
            assertThat(resolver.resolve(conflict)).isEqualTo(ConflictResolver.Resolution.REPLACE);
        }
    }
}
