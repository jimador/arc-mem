package dev.dunnam.diceanchors.anchor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("InvariantOtel — span attribute setting")
class InvariantOtelTest {

    @Nested
    @DisplayName("setInvariantSpanAttributes")
    class SetInvariantSpanAttributes {

        @Test
        @DisplayName("does not throw with noop span and violations present")
        void doesNotThrowWithNoopSpanAndViolations() {
            var violations = List.of(
                    new InvariantViolationData("r1", InvariantStrength.MUST, ProposedAction.ARCHIVE, "blocked", "a1"),
                    new InvariantViolationData("r2", InvariantStrength.SHOULD, ProposedAction.ARCHIVE, "warned", "a2")
            );
            var eval = new InvariantEvaluation(violations, 3);

            assertThatNoException().isThrownBy(() ->
                    invokeSetInvariantSpanAttributes(eval, ProposedAction.ARCHIVE));
        }

        @Test
        @DisplayName("does not throw with empty evaluation")
        void doesNotThrowWithEmptyEvaluation() {
            var eval = new InvariantEvaluation(List.of(), 0);

            assertThatNoException().isThrownBy(() ->
                    invokeSetInvariantSpanAttributes(eval, ProposedAction.DEMOTE));
        }

        @Test
        @DisplayName("does not throw with non-blocking evaluation")
        void doesNotThrowWithNonBlockingEvaluation() {
            var violations = List.of(
                    new InvariantViolationData("r1", InvariantStrength.SHOULD, ProposedAction.EVICT, "warned", "a1")
            );
            var eval = new InvariantEvaluation(violations, 2);

            assertThatNoException().isThrownBy(() ->
                    invokeSetInvariantSpanAttributes(eval, ProposedAction.EVICT));
        }
    }

    /**
     * Invokes the private setInvariantSpanAttributes method on AnchorEngine via reflection.
     * Since the method is private, and we want to test span attribute setting in isolation
     * without constructing a full AnchorEngine, we use reflection to access it.
     */
    private void invokeSetInvariantSpanAttributes(InvariantEvaluation eval, ProposedAction action) {
        try {
            Method method = AnchorEngine.class.getDeclaredMethod(
                    "setInvariantSpanAttributes", InvariantEvaluation.class, ProposedAction.class);
            method.setAccessible(true);
            // Create a minimal AnchorEngine instance - we need the constructor params
            // but since it's a private method, we test the logic via reflection with null engine
            // Actually, we can't construct without all deps, so let's just call the static-like logic directly
            // The method uses Span.current() which returns a noop span when no OTEL agent is active
            method.invoke(createMinimalEngine(), eval, action);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("setInvariantSpanAttributes threw", e.getCause());
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke setInvariantSpanAttributes", e);
        }
    }

    /**
     * Creates a minimal AnchorEngine with mock-like null dependencies.
     * Only safe because setInvariantSpanAttributes doesn't use any injected dependencies.
     */
    private AnchorEngine createMinimalEngine() {
        try {
            var constructor = AnchorEngine.class.getDeclaredConstructors()[0];
            // The constructor parameters are all interfaces/classes that we need to provide.
            // Since setInvariantSpanAttributes only uses Span.current() (noop in test),
            // we pass nulls for all dependencies. The constructor stores them but doesn't call them.
            return new AnchorEngine(
                    null, // repository
                    minimalProperties(),
                    null, // conflictDetector
                    null, // conflictResolver
                    null, // reinforcementPolicy
                    null, // decayPolicy
                    null, // eventPublisher
                    null, // trustPipeline
                    null, // canonizationGate
                    null  // invariantEvaluator
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create minimal AnchorEngine", e);
        }
    }

    private static dev.dunnam.diceanchors.DiceAnchorsProperties minimalProperties() {
        var anchorConfig = new dev.dunnam.diceanchors.DiceAnchorsProperties.AnchorConfig(
                20, 500, 100, 900, true, 0.65,
                "FAST_THEN_LLM", "TIERED",
                false, false, false,
                0.6, 400, 200, null, null, null);
        return new dev.dunnam.diceanchors.DiceAnchorsProperties(
                anchorConfig,
                new dev.dunnam.diceanchors.DiceAnchorsProperties.ChatConfig("dm", 200, null),
                new dev.dunnam.diceanchors.DiceAnchorsProperties.MemoryConfig(true, null, null, "text-embedding-3-small", 20, 5, 2),
                new dev.dunnam.diceanchors.DiceAnchorsProperties.PersistenceConfig(false),
                new dev.dunnam.diceanchors.DiceAnchorsProperties.SimConfig("gpt-4.1-mini", 30, 30, 10, true, 4),
                new dev.dunnam.diceanchors.DiceAnchorsProperties.ConflictDetectionConfig("llm", "gpt-4o-nano"),
                new dev.dunnam.diceanchors.DiceAnchorsProperties.RunHistoryConfig("memory"),
                new dev.dunnam.diceanchors.DiceAnchorsProperties.AssemblyConfig(0),
                null, null);
    }
}
