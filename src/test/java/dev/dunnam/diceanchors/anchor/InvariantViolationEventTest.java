package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.anchor.event.AnchorLifecycleEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnchorLifecycleEvent.InvariantViolation")
class InvariantViolationEventTest {

    private static final String CONTEXT_ID = "ctx-event";
    private static final Object SOURCE = new Object();

    @Nested
    @DisplayName("factory with explicit parameters")
    class ExplicitFactory {

        @Test
        @DisplayName("creates correct instance with all fields")
        void createsCorrectInstance() {
            var event = AnchorLifecycleEvent.invariantViolation(
                    SOURCE, CONTEXT_ID,
                    "rule-1", InvariantStrength.MUST,
                    ProposedAction.ARCHIVE, "Archive is prohibited", "anchor-1");

            assertThat(event.getRuleId()).isEqualTo("rule-1");
            assertThat(event.getStrength()).isEqualTo(InvariantStrength.MUST);
            assertThat(event.getBlockedAction()).isEqualTo(ProposedAction.ARCHIVE);
            assertThat(event.getConstraintDescription()).isEqualTo("Archive is prohibited");
            assertThat(event.getAnchorId()).isEqualTo("anchor-1");
            assertThat(event.getContextId()).isEqualTo(CONTEXT_ID);
            assertThat(event.getOccurredAt()).isNotNull();
        }

        @Test
        @DisplayName("anchorId can be null")
        void anchorIdCanBeNull() {
            var event = AnchorLifecycleEvent.invariantViolation(
                    SOURCE, CONTEXT_ID,
                    "rule-2", InvariantStrength.SHOULD,
                    ProposedAction.EVICT, "Context-wide check", null);

            assertThat(event.getAnchorId()).isNull();
        }
    }

    @Nested
    @DisplayName("factory from InvariantViolationData")
    class DataFactory {

        @Test
        @DisplayName("creates event from violation data record")
        void createsFromViolationData() {
            var data = new InvariantViolationData(
                    "rule-3", InvariantStrength.MUST,
                    ProposedAction.DEMOTE, "Demotion blocked", "anchor-2");

            var event = AnchorLifecycleEvent.invariantViolation(SOURCE, CONTEXT_ID, data);

            assertThat(event.getRuleId()).isEqualTo("rule-3");
            assertThat(event.getStrength()).isEqualTo(InvariantStrength.MUST);
            assertThat(event.getBlockedAction()).isEqualTo(ProposedAction.DEMOTE);
            assertThat(event.getConstraintDescription()).isEqualTo("Demotion blocked");
            assertThat(event.getAnchorId()).isEqualTo("anchor-2");
            assertThat(event.getContextId()).isEqualTo(CONTEXT_ID);
        }

        @Test
        @DisplayName("preserves null anchorId from violation data")
        void preservesNullAnchorId() {
            var data = new InvariantViolationData(
                    "rule-4", InvariantStrength.SHOULD,
                    ProposedAction.AUTHORITY_CHANGE, "Context check", null);

            var event = AnchorLifecycleEvent.invariantViolation(SOURCE, CONTEXT_ID, data);

            assertThat(event.getAnchorId()).isNull();
        }
    }

    @Nested
    @DisplayName("sealed hierarchy")
    class SealedHierarchy {

        @Test
        @DisplayName("InvariantViolation is an instance of AnchorLifecycleEvent")
        void isInstanceOfAnchorLifecycleEvent() {
            var event = AnchorLifecycleEvent.invariantViolation(
                    SOURCE, CONTEXT_ID,
                    "rule-5", InvariantStrength.MUST,
                    ProposedAction.ARCHIVE, "Blocked", "a1");

            assertThat(event).isInstanceOf(AnchorLifecycleEvent.class);
            assertThat(event).isInstanceOf(AnchorLifecycleEvent.InvariantViolation.class);
        }

        @Test
        @DisplayName("can be matched in switch expression with other event types")
        void canBeMatchedInSwitch() {
            AnchorLifecycleEvent event = AnchorLifecycleEvent.invariantViolation(
                    SOURCE, CONTEXT_ID,
                    "rule-6", InvariantStrength.MUST,
                    ProposedAction.ARCHIVE, "Blocked", "a1");

            var result = switch (event) {
                case AnchorLifecycleEvent.InvariantViolation v -> "violation:" + v.getRuleId();
                case AnchorLifecycleEvent.Promoted _ -> "promoted";
                case AnchorLifecycleEvent.Reinforced _ -> "reinforced";
                case AnchorLifecycleEvent.Archived _ -> "archived";
                case AnchorLifecycleEvent.Evicted _ -> "evicted";
                case AnchorLifecycleEvent.ConflictDetected _ -> "conflict-detected";
                case AnchorLifecycleEvent.ConflictResolved _ -> "conflict-resolved";
                case AnchorLifecycleEvent.AuthorityChanged _ -> "authority-changed";
                case AnchorLifecycleEvent.TierChanged _ -> "tier-changed";
                case AnchorLifecycleEvent.Superseded _ -> "superseded";
            };

            assertThat(result).isEqualTo("violation:rule-6");
        }
    }

    @Nested
    @DisplayName("InvariantViolationData record")
    class ViolationDataRecord {

        @Test
        @DisplayName("construction and field access")
        void constructionAndFieldAccess() {
            var data = new InvariantViolationData(
                    "rule-7", InvariantStrength.SHOULD,
                    ProposedAction.EVICT, "Eviction warning", "anchor-3");

            assertThat(data.ruleId()).isEqualTo("rule-7");
            assertThat(data.strength()).isEqualTo(InvariantStrength.SHOULD);
            assertThat(data.blockedAction()).isEqualTo(ProposedAction.EVICT);
            assertThat(data.constraintDescription()).isEqualTo("Eviction warning");
            assertThat(data.anchorId()).isEqualTo("anchor-3");
        }
    }
}
