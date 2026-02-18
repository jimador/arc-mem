package dev.dunnam.diceanchors.anchor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthorityConflictResolver")
class AuthorityConflictResolverTest {

    private final AuthorityConflictResolver resolver = new AuthorityConflictResolver();

    @Test
    @DisplayName("keeps existing when anchor has RELIABLE authority")
    void keepsExistingReliable() {
        var conflict = new ConflictDetector.Conflict(
                Anchor.withoutTrust("1", "King is dead", 700, Authority.RELIABLE, false, 0.9, 0),
                "King is alive", 0.7, "negation"
        );
        assertThat(resolver.resolve(conflict)).isEqualTo(ConflictResolver.Resolution.KEEP_EXISTING);
    }

    @Test
    @DisplayName("keeps existing when anchor has CANON authority")
    void keepsExistingCanon() {
        var conflict = new ConflictDetector.Conflict(
                Anchor.withoutTrust("1", "King is dead", 850, Authority.CANON, true, 0.99, 5),
                "King is alive", 0.9, "negation"
        );
        assertThat(resolver.resolve(conflict)).isEqualTo(ConflictResolver.Resolution.KEEP_EXISTING);
    }

    @Test
    @DisplayName("replaces PROVISIONAL anchor with high-confidence incoming")
    void replacesProvisionalHighConfidence() {
        var conflict = new ConflictDetector.Conflict(
                Anchor.withoutTrust("1", "Something uncertain", 300, Authority.PROVISIONAL, false, 0.5, 0),
                "Something more certain", 0.9, "contradiction"
        );
        assertThat(resolver.resolve(conflict)).isEqualTo(ConflictResolver.Resolution.REPLACE);
    }

    @Test
    @DisplayName("coexists for PROVISIONAL anchor with moderate confidence")
    void coexistsProvisionalModerateConfidence() {
        var conflict = new ConflictDetector.Conflict(
                Anchor.withoutTrust("1", "Maybe this", 400, Authority.PROVISIONAL, false, 0.6, 0),
                "Maybe that", 0.6, "partial overlap"
        );
        assertThat(resolver.resolve(conflict)).isEqualTo(ConflictResolver.Resolution.COEXIST);
    }
}
