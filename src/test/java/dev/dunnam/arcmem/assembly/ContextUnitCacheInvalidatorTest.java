package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.AuthorityChangeDirection;
import dev.dunnam.diceanchors.anchor.event.AnchorLifecycleEvent;
import dev.dunnam.diceanchors.anchor.event.ArchiveReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnchorCacheInvalidator")
class AnchorCacheInvalidatorTest {

    private static final String CTX_1 = "ctx-1";
    private static final String CTX_2 = "ctx-2";
    private static final Object SOURCE = new Object();

    private AnchorCacheInvalidator invalidator;

    @BeforeEach
    void setUp() {
        invalidator = new AnchorCacheInvalidator();
    }

    @Nested
    @DisplayName("Promoted event marks context dirty")
    class PromotedEventMarksDirty {

        @Test
        @DisplayName("Promoted event marks the event's context as dirty")
        void promotedEventMarksDirty() {
            var event = AnchorLifecycleEvent.promoted(SOURCE, CTX_1, "p1", "a1", 500);

            invalidator.onLifecycleEvent(event);

            assertThat(invalidator.isDirty(CTX_1)).isTrue();
        }
    }

    @Nested
    @DisplayName("markClean")
    class MarkClean {

        @Test
        @DisplayName("markClean clears the dirty flag for a context")
        void markCleanClearsDirtyFlag() {
            invalidator.onLifecycleEvent(AnchorLifecycleEvent.promoted(SOURCE, CTX_1, "p1", "a1", 500));
            assertThat(invalidator.isDirty(CTX_1)).isTrue();

            invalidator.markClean(CTX_1);

            assertThat(invalidator.isDirty(CTX_1)).isFalse();
        }
    }

    @Nested
    @DisplayName("context isolation")
    class ContextIsolation {

        @Test
        @DisplayName("dirty flag for ctx-1 does not affect ctx-2")
        void otherContextsUnaffected() {
            invalidator.onLifecycleEvent(AnchorLifecycleEvent.promoted(SOURCE, CTX_1, "p1", "a1", 500));

            assertThat(invalidator.isDirty(CTX_2)).isFalse();
        }
    }

    @Nested
    @DisplayName("AuthorityChanged event marks dirty")
    class AuthorityChangedMarksDirty {

        @Test
        @DisplayName("AuthorityChanged event marks the event's context as dirty")
        void authorityChangedMarksDirty() {
            var event = AnchorLifecycleEvent.authorityChanged(
                    SOURCE, CTX_1, "a1",
                    Authority.PROVISIONAL, Authority.UNRELIABLE,
                    AuthorityChangeDirection.PROMOTED, "reinforcement");

            invalidator.onLifecycleEvent(event);

            assertThat(invalidator.isDirty(CTX_1)).isTrue();
        }
    }

    @Nested
    @DisplayName("Archived event marks dirty")
    class ArchivedMarksDirty {

        @Test
        @DisplayName("Archived event marks the event's context as dirty")
        void archivedEventMarksDirty() {
            var event = AnchorLifecycleEvent.archived(SOURCE, CTX_1, "a1", ArchiveReason.DORMANCY_DECAY);

            invalidator.onLifecycleEvent(event);

            assertThat(invalidator.isDirty(CTX_1)).isTrue();
        }
    }
}
