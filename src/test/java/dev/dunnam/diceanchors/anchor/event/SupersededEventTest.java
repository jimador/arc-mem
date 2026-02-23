package dev.dunnam.diceanchors.anchor.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnchorLifecycleEvent.Superseded")
class SupersededEventTest {

    private static final String CONTEXT_ID = "ctx-test";

    @Nested
    @DisplayName("Factory method")
    class FactoryMethod {

        @Test
        @DisplayName("superseded() creates event with correct predecessor and successor")
        void supersededCreatesEventWithCorrectIds() {
            var event = AnchorLifecycleEvent.superseded(
                    this, CONTEXT_ID, "pred-1", "succ-2",
                    SupersessionReason.CONFLICT_REPLACEMENT);

            assertThat(event.getPredecessorId()).isEqualTo("pred-1");
            assertThat(event.getSuccessorId()).isEqualTo("succ-2");
        }

        @Test
        @DisplayName("superseded() creates event with correct reason")
        void supersededCreatesEventWithCorrectReason() {
            var event = AnchorLifecycleEvent.superseded(
                    this, CONTEXT_ID, "pred-1", "succ-2",
                    SupersessionReason.BUDGET_EVICTION);

            assertThat(event.getReason()).isEqualTo(SupersessionReason.BUDGET_EVICTION);
        }

        @Test
        @DisplayName("superseded() creates event with correct contextId")
        void supersededCreatesEventWithCorrectContextId() {
            var event = AnchorLifecycleEvent.superseded(
                    this, CONTEXT_ID, "pred-1", "succ-2",
                    SupersessionReason.MANUAL);

            assertThat(event.getContextId()).isEqualTo(CONTEXT_ID);
        }

        @Test
        @DisplayName("superseded() creates event with occurredAt timestamp")
        void supersededCreatesEventWithOccurredAt() {
            var event = AnchorLifecycleEvent.superseded(
                    this, CONTEXT_ID, "pred-1", "succ-2",
                    SupersessionReason.DECAY_DEMOTION);

            assertThat(event.getOccurredAt()).isNotNull();
        }

        @Test
        @DisplayName("superseded() creates an instance of AnchorLifecycleEvent")
        void supersededIsInstanceOfAnchorLifecycleEvent() {
            var event = AnchorLifecycleEvent.superseded(
                    this, CONTEXT_ID, "pred-1", "succ-2",
                    SupersessionReason.CONFLICT_REPLACEMENT);

            assertThat(event).isInstanceOf(AnchorLifecycleEvent.class);
            assertThat(event).isInstanceOf(AnchorLifecycleEvent.Superseded.class);
        }
    }
}
