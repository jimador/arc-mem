package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties.TierModifierConfig;
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

    @Nested
    @DisplayName("Tier-aware resolution")
    class TierAwareResolution {

        private final TierModifierConfig tierModifiers = new TierModifierConfig(0.1, 0.0, -0.1);
        private final AuthorityConflictResolver tierResolver = new AuthorityConflictResolver(0.8, 0.6, tierModifiers);

        @Test
        @DisplayName("HOT anchor raises effective threshold making 0.85 insufficient for REPLACE")
        void hotAnchorHarderToReplace() {
            var anchor = new Anchor("1", "Hot reliable fact", 700, Authority.RELIABLE, false, 0.9, 3,
                    null, 0.0, 1.0, MemoryTier.HOT);
            var conflict = new ConflictDetector.Conflict(anchor, "High-confidence counter", 0.85, "negation");

            // effective replace = 0.8 + 0.1 = 0.9; 0.85 < 0.9 -> DEMOTE_EXISTING
            assertThat(tierResolver.resolve(conflict)).isEqualTo(ConflictResolver.Resolution.DEMOTE_EXISTING);
        }

        @Test
        @DisplayName("COLD anchor lowers effective threshold making 0.75 sufficient for REPLACE")
        void coldAnchorEasierToReplace() {
            var anchor = new Anchor("1", "Cold reliable fact", 700, Authority.RELIABLE, false, 0.9, 3,
                    null, 0.0, 1.0, MemoryTier.COLD);
            var conflict = new ConflictDetector.Conflict(anchor, "Moderate counter", 0.75, "negation");

            // effective replace = 0.8 + (-0.1) = 0.7; 0.75 >= 0.7 -> REPLACE
            assertThat(tierResolver.resolve(conflict)).isEqualTo(ConflictResolver.Resolution.REPLACE);
        }

        @Test
        @DisplayName("WARM anchor at baseline threshold behaves like default resolver")
        void warmAnchorAtBaseline() {
            var anchor = new Anchor("1", "Warm reliable fact", 700, Authority.RELIABLE, false, 0.9, 3,
                    null, 0.0, 1.0, MemoryTier.WARM);
            var conflict = new ConflictDetector.Conflict(anchor, "High-confidence counter", 0.85, "negation");

            // effective replace = 0.8 + 0.0 = 0.8; 0.85 >= 0.8 -> REPLACE
            assertThat(tierResolver.resolve(conflict)).isEqualTo(ConflictResolver.Resolution.REPLACE);
        }

        @Test
        @DisplayName("CANON anchor is immune regardless of tier")
        void canonImmuneRegardlessOfTier() {
            var anchor = new Anchor("1", "Canon cold fact", 850, Authority.CANON, true, 0.99, 5,
                    null, 0.0, 1.0, MemoryTier.COLD);
            var conflict = new ConflictDetector.Conflict(anchor, "Very high counter", 0.95, "negation");

            assertThat(tierResolver.resolve(conflict)).isEqualTo(ConflictResolver.Resolution.KEEP_EXISTING);
        }
    }

    @Nested
    @DisplayName("confidenceBand")
    class ConfidenceBand {

        @Test
        @DisplayName("returns LOW for confidence below 0.4")
        void lowBand() {
            assertThat(AuthorityConflictResolver.confidenceBand(0.3)).isEqualTo("LOW");
        }

        @Test
        @DisplayName("returns MEDIUM for confidence in [0.4, 0.8]")
        void mediumBand() {
            assertThat(AuthorityConflictResolver.confidenceBand(0.6)).isEqualTo("MEDIUM");
        }

        @Test
        @DisplayName("returns HIGH for confidence above 0.8")
        void highBand() {
            assertThat(AuthorityConflictResolver.confidenceBand(0.9)).isEqualTo("HIGH");
        }
    }
}
