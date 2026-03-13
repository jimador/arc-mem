package dev.arcmem.core.memory.model;
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

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Authority")
class AuthorityTest {

    @Nested
    @DisplayName("isAtLeast")
    class IsAtLeast {

        @Test
        @DisplayName("returns true when comparing to the same level")
        void isAtLeast_sameLevel_returnsTrue() {
            assertThat(Authority.PROVISIONAL.isAtLeast(Authority.PROVISIONAL)).isTrue();
            assertThat(Authority.UNRELIABLE.isAtLeast(Authority.UNRELIABLE)).isTrue();
            assertThat(Authority.RELIABLE.isAtLeast(Authority.RELIABLE)).isTrue();
            assertThat(Authority.CANON.isAtLeast(Authority.CANON)).isTrue();
        }

        @Test
        @DisplayName("returns true when comparing to a lower level")
        void isAtLeast_higherLevel_returnsTrue() {
            assertThat(Authority.CANON.isAtLeast(Authority.RELIABLE)).isTrue();
            assertThat(Authority.CANON.isAtLeast(Authority.UNRELIABLE)).isTrue();
            assertThat(Authority.CANON.isAtLeast(Authority.PROVISIONAL)).isTrue();
            assertThat(Authority.RELIABLE.isAtLeast(Authority.PROVISIONAL)).isTrue();
            assertThat(Authority.UNRELIABLE.isAtLeast(Authority.PROVISIONAL)).isTrue();
        }

        @Test
        @DisplayName("returns false when comparing to a higher level")
        void isAtLeast_lowerLevel_returnsFalse() {
            assertThat(Authority.PROVISIONAL.isAtLeast(Authority.UNRELIABLE)).isFalse();
            assertThat(Authority.PROVISIONAL.isAtLeast(Authority.RELIABLE)).isFalse();
            assertThat(Authority.PROVISIONAL.isAtLeast(Authority.CANON)).isFalse();
            assertThat(Authority.UNRELIABLE.isAtLeast(Authority.RELIABLE)).isFalse();
            assertThat(Authority.RELIABLE.isAtLeast(Authority.CANON)).isFalse();
        }
    }

    @Nested
    @DisplayName("level ordering")
    class LevelOrdering {

        @Test
        @DisplayName("CANON has the highest level value among all authorities")
        void levelOrdering_canonHighest() {
            assertThat(Authority.CANON.level()).isGreaterThan(Authority.RELIABLE.level());
            assertThat(Authority.RELIABLE.level()).isGreaterThan(Authority.UNRELIABLE.level());
            assertThat(Authority.UNRELIABLE.level()).isGreaterThan(Authority.PROVISIONAL.level());
        }
    }

    @Nested
    @DisplayName("previousLevel")
    class PreviousLevel {

        @Test
        @DisplayName("CANON returns RELIABLE")
        void canonReturnsReliable() {
            assertThat(Authority.CANON.previousLevel()).isEqualTo(Authority.RELIABLE);
        }

        @Test
        @DisplayName("RELIABLE returns UNRELIABLE")
        void reliableReturnsUnreliable() {
            assertThat(Authority.RELIABLE.previousLevel()).isEqualTo(Authority.UNRELIABLE);
        }

        @Test
        @DisplayName("UNRELIABLE returns PROVISIONAL")
        void unreliableReturnsProvisional() {
            assertThat(Authority.UNRELIABLE.previousLevel()).isEqualTo(Authority.PROVISIONAL);
        }

        @Test
        @DisplayName("PROVISIONAL returns PROVISIONAL (floor)")
        void provisionalReturnsProvisional() {
            assertThat(Authority.PROVISIONAL.previousLevel()).isEqualTo(Authority.PROVISIONAL);
        }
    }
}
