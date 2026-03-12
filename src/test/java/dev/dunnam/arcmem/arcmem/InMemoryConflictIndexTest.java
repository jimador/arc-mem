package dev.dunnam.diceanchors.anchor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryConflictIndex")
class InMemoryConflictIndexTest {

    private InMemoryConflictIndex index;

    @BeforeEach
    void setUp() {
        index = new InMemoryConflictIndex();
    }

    private ConflictEntry entry(String anchorId, String text) {
        return new ConflictEntry(anchorId, text, Authority.RELIABLE,
                ConflictType.CONTRADICTION, 0.9, Instant.now());
    }

    @Nested
    @DisplayName("getConflicts")
    class GetConflicts {

        @Test
        @DisplayName("returns empty set for unknown anchor")
        void unknownAnchor_returnsEmpty() {
            assertThat(index.getConflicts("unknown")).isEmpty();
        }

        @Test
        @DisplayName("returns recorded entries for known anchor")
        void recordedEntries_returned() {
            var e = entry("a1", "The king is dead");
            index.recordConflict("a1", e);

            assertThat(index.getConflicts("a1")).containsExactly(e);
        }

        @Test
        @DisplayName("returns defensive copy — modifying returned set does not affect index")
        void defensiveCopy_modificationHasNoEffect() {
            var e = entry("a1", "The king is dead");
            index.recordConflict("a1", e);

            var returned = index.getConflicts("a1");
            var mutable = new HashSet<>(returned);
            mutable.clear();

            assertThat(index.getConflicts("a1")).containsExactly(e);
        }
    }

    @Nested
    @DisplayName("recordConflict")
    class RecordConflict {

        @Test
        @DisplayName("idempotent — recording same entry twice produces one entry")
        void idempotent_duplicateIgnored() {
            var e = entry("a1", "The sky is blue");
            index.recordConflict("a1", e);
            index.recordConflict("a1", e);

            assertThat(index.getConflicts("a1")).hasSize(1);
        }

        @Test
        @DisplayName("entries for different anchors are independent")
        void differentAnchors_independent() {
            var e1 = entry("a1", "The sky is blue");
            var e2 = entry("a2", "The sky is red");
            index.recordConflict("a1", e1);
            index.recordConflict("a2", e2);

            assertThat(index.getConflicts("a1")).containsExactly(e1);
            assertThat(index.getConflicts("a2")).containsExactly(e2);
        }
    }

    @Nested
    @DisplayName("removeConflicts")
    class RemoveConflicts {

        @Test
        @DisplayName("removes all entries for the given anchor")
        void removesEntriesForAnchor() {
            index.recordConflict("a1", entry("a1", "text1"));
            index.removeConflicts("a1");

            assertThat(index.getConflicts("a1")).isEmpty();
        }

        @Test
        @DisplayName("removes cross-references to anchor from other anchors' sets")
        void removesCrossReferencesFromOtherAnchors() {
            var backRef = entry("a1", "referenced-by-a2");
            index.recordConflict("a2", backRef);
            index.removeConflicts("a1");

            assertThat(index.getConflicts("a2")).isEmpty();
        }

        @Test
        @DisplayName("no-op for unknown anchor")
        void unknownAnchor_noOp() {
            index.recordConflict("a1", entry("a1", "text"));
            index.removeConflicts("unknown");

            assertThat(index.getConflicts("a1")).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        @DisplayName("removes all entries for anchors in the given context")
        void clear_removesContextEntries() {
            index.registerAnchor("a1", "ctx-1");
            index.recordConflict("a1", entry("a1", "text"));

            index.clear("ctx-1");

            assertThat(index.getConflicts("a1")).isEmpty();
        }

        @Test
        @DisplayName("entries for other contexts are unaffected")
        void clear_otherContextsUnaffected() {
            index.registerAnchor("a1", "ctx-1");
            index.registerAnchor("a2", "ctx-2");
            index.recordConflict("a1", entry("a1", "text1"));
            index.recordConflict("a2", entry("a2", "text2"));

            index.clear("ctx-1");

            assertThat(index.getConflicts("a1")).isEmpty();
            assertThat(index.getConflicts("a2")).isNotEmpty();
        }

        @Test
        @DisplayName("no-op for unknown context")
        void unknownContext_noOp() {
            index.registerAnchor("a1", "ctx-1");
            index.recordConflict("a1", entry("a1", "text"));

            index.clear("unknown-ctx");

            assertThat(index.getConflicts("a1")).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("hasConflicts and size")
    class HasConflictsAndSize {

        @Test
        @DisplayName("hasConflicts reflects recorded entries")
        void hasConflicts_reflectsRecordedEntries() {
            assertThat(index.hasConflicts("a1")).isFalse();

            index.recordConflict("a1", entry("a1", "text"));
            assertThat(index.hasConflicts("a1")).isTrue();

            index.removeConflicts("a1");
            assertThat(index.hasConflicts("a1")).isFalse();
        }

        @Test
        @DisplayName("size equals total number of recorded entries across all anchors")
        void size_totalAcrossAllAnchors() {
            assertThat(index.size()).isZero();

            index.recordConflict("a1", entry("a1", "text1"));
            index.recordConflict("a1", entry("a1x", "text1x"));
            index.recordConflict("a2", entry("a2", "text2"));
            assertThat(index.size()).isEqualTo(3);

            index.removeConflicts("a1");
            assertThat(index.size()).isEqualTo(1);
        }
    }
}
