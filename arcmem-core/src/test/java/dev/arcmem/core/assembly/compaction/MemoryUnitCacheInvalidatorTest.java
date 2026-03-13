package dev.arcmem.core.assembly.compaction;
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

import dev.arcmem.core.memory.event.MemoryUnitLifecycleEvent;
import dev.arcmem.core.memory.event.ArchiveReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MemoryUnitCacheInvalidator")
class MemoryUnitCacheInvalidatorTest {

    private static final String CTX_1 = "ctx-1";
    private static final String CTX_2 = "ctx-2";
    private static final Object SOURCE = new Object();

    private MemoryUnitCacheInvalidator invalidator;

    @BeforeEach
    void setUp() {
        invalidator = new MemoryUnitCacheInvalidator();
    }

    @Nested
    @DisplayName("Promoted event marks context dirty")
    class PromotedEventMarksDirty {

        @Test
        @DisplayName("Promoted event marks the event's context as dirty")
        void promotedEventMarksDirty() {
            var event = MemoryUnitLifecycleEvent.promoted(SOURCE, CTX_1, "p1", "a1", 500);

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
            invalidator.onLifecycleEvent(MemoryUnitLifecycleEvent.promoted(SOURCE, CTX_1, "p1", "a1", 500));
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
            invalidator.onLifecycleEvent(MemoryUnitLifecycleEvent.promoted(SOURCE, CTX_1, "p1", "a1", 500));

            assertThat(invalidator.isDirty(CTX_2)).isFalse();
        }
    }

    @Nested
    @DisplayName("AuthorityChanged event marks dirty")
    class AuthorityChangedMarksDirty {

        @Test
        @DisplayName("AuthorityChanged event marks the event's context as dirty")
        void authorityChangedMarksDirty() {
            var event = MemoryUnitLifecycleEvent.authorityChanged(
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
            var event = MemoryUnitLifecycleEvent.archived(SOURCE, CTX_1, "a1", ArchiveReason.DORMANCY_DECAY);

            invalidator.onLifecycleEvent(event);

            assertThat(invalidator.isDirty(CTX_1)).isTrue();
        }
    }
}
