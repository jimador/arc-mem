package dev.dunnam.diceanchors.anchor;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RevisionAwareConflictResolver")
class RevisionAwareConflictResolverTest {

    private static AnchorMutationStrategy permissive() {
        return request -> new MutationDecision.Allow();
    }

    private static final AnchorMutationStrategy HITL_ONLY = new HitlOnlyMutationStrategy();

    private ConflictDetector.Conflict conflict(Authority authority, double confidence, ConflictType type) {
        var anchor = Anchor.withoutTrust("a1", "anchor", 500, authority, false, 0.9, 0);
        return new ConflictDetector.Conflict(anchor, "incoming", confidence, "reasoning",
                ConflictDetector.DetectionQuality.FULL, type);
    }

    @Nested
    @DisplayName("revision dispatch (permissive strategy)")
    class RevisionDispatch {

        @Test
        @DisplayName("accepts REVISION for PROVISIONAL")
        void acceptsRevisionForProvisional() {
            var delegate = mock(AuthorityConflictResolver.class);
            var resolver = new RevisionAwareConflictResolver(delegate, permissive(), true, false, 0.75, 0.8);

            var resolution = resolver.resolve(conflict(Authority.PROVISIONAL, 0.3, ConflictType.REVISION));

            assertThat(resolution).isEqualTo(ConflictResolver.Resolution.REPLACE);
        }

        @Test
        @DisplayName("accepts REVISION for UNRELIABLE above threshold")
        void acceptsRevisionForUnreliableAboveThreshold() {
            var delegate = mock(AuthorityConflictResolver.class);
            var resolver = new RevisionAwareConflictResolver(delegate, permissive(), true, false, 0.75, 0.8);

            var resolution = resolver.resolve(conflict(Authority.UNRELIABLE, 0.8, ConflictType.REVISION));

            assertThat(resolution).isEqualTo(ConflictResolver.Resolution.REPLACE);
        }

        @Test
        @DisplayName("rejects REVISION for CANON")
        void rejectsRevisionForCanon() {
            var delegate = mock(AuthorityConflictResolver.class);
            var resolver = new RevisionAwareConflictResolver(delegate, permissive(), true, false, 0.75, 0.8);

            var resolution = resolver.resolve(conflict(Authority.CANON, 0.99, ConflictType.REVISION));

            assertThat(resolution).isEqualTo(ConflictResolver.Resolution.KEEP_EXISTING);
        }

        @Test
        @DisplayName("delegates REVISION for RELIABLE when not revisable")
        void delegatesRevisionForReliableWhenNotRevisable() {
            var delegate = mock(AuthorityConflictResolver.class);
            when(delegate.resolve(Mockito.any())).thenReturn(ConflictResolver.Resolution.DEMOTE_EXISTING);
            var resolver = new RevisionAwareConflictResolver(delegate, permissive(), true, false, 0.75, 0.8);

            var resolution = resolver.resolve(conflict(Authority.RELIABLE, 0.95, ConflictType.REVISION));

            assertThat(resolution).isEqualTo(ConflictResolver.Resolution.DEMOTE_EXISTING);
            verify(delegate).resolve(Mockito.any());
        }

        @Test
        @DisplayName("accepts REVISION for RELIABLE when enabled and above replace threshold")
        void acceptsRevisionForReliableWhenEnabledAndAboveReplaceThreshold() {
            var delegate = mock(AuthorityConflictResolver.class);
            var resolver = new RevisionAwareConflictResolver(delegate, permissive(), true, true, 0.75, 0.8);

            var resolution = resolver.resolve(conflict(Authority.RELIABLE, 0.85, ConflictType.REVISION));

            assertThat(resolution).isEqualTo(ConflictResolver.Resolution.REPLACE);
        }

        @Test
        @DisplayName("delegates below-threshold REVISION for UNRELIABLE")
        void delegatesBelowThresholdRevisionForUnreliable() {
            var delegate = mock(AuthorityConflictResolver.class);
            when(delegate.resolve(Mockito.any())).thenReturn(ConflictResolver.Resolution.KEEP_EXISTING);
            var resolver = new RevisionAwareConflictResolver(delegate, permissive(), true, true, 0.75, 0.8);

            var resolution = resolver.resolve(conflict(Authority.UNRELIABLE, 0.6, ConflictType.REVISION));

            assertThat(resolution).isEqualTo(ConflictResolver.Resolution.KEEP_EXISTING);
            verify(delegate).resolve(Mockito.any());
        }

        @Test
        @DisplayName("returns COEXIST for WORLD_PROGRESSION")
        void returnsCoexistForWorldProgression() {
            var delegate = mock(AuthorityConflictResolver.class);
            var resolver = new RevisionAwareConflictResolver(delegate, permissive(), true, true, 0.75, 0.8);

            var resolution = resolver.resolve(conflict(Authority.RELIABLE, 0.9, ConflictType.WORLD_PROGRESSION));

            assertThat(resolution).isEqualTo(ConflictResolver.Resolution.COEXIST);
        }

        @Test
        @DisplayName("treats null conflictType as CONTRADICTION and delegates")
        void treatsNullConflictTypeAsContradictionAndDelegates() {
            var delegate = mock(AuthorityConflictResolver.class);
            when(delegate.resolve(Mockito.any())).thenReturn(ConflictResolver.Resolution.DEMOTE_EXISTING);
            var resolver = new RevisionAwareConflictResolver(delegate, permissive(), true, true, 0.75, 0.8);
            var conflict = conflict(Authority.RELIABLE, 0.9, null);

            var resolution = resolver.resolve(conflict);

            assertThat(resolution).isEqualTo(ConflictResolver.Resolution.DEMOTE_EXISTING);
            verify(delegate).resolve(conflict);
        }
    }

    @Nested
    @DisplayName("HITL-only strategy")
    class HitlOnlyBehavior {

        @Test
        @DisplayName("REVISION conflict delegates to authority resolver under HITL-only")
        void revisionDelegatesToAuthorityResolverUnderHitlOnly() {
            var delegate = mock(AuthorityConflictResolver.class);
            when(delegate.resolve(Mockito.any())).thenReturn(ConflictResolver.Resolution.KEEP_EXISTING);
            var resolver = new RevisionAwareConflictResolver(delegate, HITL_ONLY, true, false, 0.75, 0.8);

            var resolution = resolver.resolve(conflict(Authority.PROVISIONAL, 0.9, ConflictType.REVISION));

            assertThat(resolution).isEqualTo(ConflictResolver.Resolution.KEEP_EXISTING);
            verify(delegate).resolve(Mockito.any());
        }

        @Test
        @DisplayName("OTEL attributes still recorded under HITL-only")
        void otelAttributesStillRecordedUnderHitlOnly() {
            var delegate = mock(AuthorityConflictResolver.class);
            when(delegate.resolve(Mockito.any())).thenReturn(ConflictResolver.Resolution.KEEP_EXISTING);
            var resolver = new RevisionAwareConflictResolver(delegate, HITL_ONLY, true, false, 0.75, 0.8);
            var span = new RecordingSpan();
            var conflict = conflict(Authority.PROVISIONAL, 0.9, ConflictType.REVISION);

            try (Scope ignored = Context.root().with(span).makeCurrent()) {
                resolver.resolve(conflict);
            }

            assertThat(span.stringAttributes.get("conflict.type")).isEqualTo("REVISION");
            assertThat(span.stringAttributes.get("conflict.type.reasoning")).isEqualTo("reasoning");
            assertThat(span.stringAttributes.get("conflict.revision.reason")).isEqualTo("mutation_strategy_denied");
        }

        @Test
        @DisplayName("CONTRADICTION still delegates normally under HITL-only")
        void contradictionStillDelegatesNormallyUnderHitlOnly() {
            var delegate = mock(AuthorityConflictResolver.class);
            when(delegate.resolve(Mockito.any())).thenReturn(ConflictResolver.Resolution.DEMOTE_EXISTING);
            var resolver = new RevisionAwareConflictResolver(delegate, HITL_ONLY, true, false, 0.75, 0.8);

            var resolution = resolver.resolve(conflict(Authority.RELIABLE, 0.9, ConflictType.CONTRADICTION));

            assertThat(resolution).isEqualTo(ConflictResolver.Resolution.DEMOTE_EXISTING);
            verify(delegate).resolve(Mockito.any());
        }
    }

    @Test
    @DisplayName("sets OTEL attributes for type reasoning and decision")
    void setsOtelAttributesForTypeReasoningAndDecision() {
        var delegate = mock(AuthorityConflictResolver.class);
        var resolver = new RevisionAwareConflictResolver(delegate, permissive(), true, true, 0.75, 0.8);
        var span = new RecordingSpan();
        var conflict = conflict(Authority.RELIABLE, 0.9, ConflictType.REVISION);

        try (Scope ignored = Context.root().with(span).makeCurrent()) {
            resolver.resolve(conflict);
        }

        assertThat(span.stringAttributes.get("conflict.type")).isEqualTo("REVISION");
        assertThat(span.stringAttributes.get("conflict.type.reasoning")).isEqualTo("reasoning");
        assertThat(span.stringAttributes.get("conflict.revision.decision")).isEqualTo("accepted");
        assertThat(span.stringAttributes.get("conflict.revision.reason"))
                .isEqualTo("reliable_above_replace_threshold");
        assertThat(span.booleanAttributes.get("conflict.revision.eligible")).isTrue();
    }

    private static final class RecordingSpan implements Span {

        private final Map<String, String> stringAttributes = new HashMap<>();
        private final Map<String, Boolean> booleanAttributes = new HashMap<>();

        @Override
        public Span setAttribute(String key, String value) {
            stringAttributes.put(key, value);
            return this;
        }

        @Override
        public Span setAttribute(String key, long value) {
            return this;
        }

        @Override
        public Span setAttribute(String key, double value) {
            return this;
        }

        @Override
        public Span setAttribute(String key, boolean value) {
            booleanAttributes.put(key, value);
            return this;
        }

        @Override
        public <T> Span setAttribute(AttributeKey<T> key, T value) {
            return this;
        }

        @Override
        public Span addEvent(String name, Attributes attributes) {
            return this;
        }

        @Override
        public Span addEvent(String name, Attributes attributes, long timestamp, TimeUnit unit) {
            return this;
        }

        @Override
        public Span setStatus(StatusCode statusCode, String description) {
            return this;
        }

        @Override
        public Span recordException(Throwable exception) {
            return this;
        }

        @Override
        public Span recordException(Throwable exception, Attributes additionalAttributes) {
            return this;
        }

        @Override
        public Span updateName(String name) {
            return this;
        }

        @Override
        public void end() {
        }

        @Override
        public void end(long timestamp, TimeUnit unit) {
        }

        @Override
        public SpanContext getSpanContext() {
            return SpanContext.getInvalid();
        }

        @Override
        public boolean isRecording() {
            return true;
        }
    }
}
