package dev.arcmem.simulator.compaction;

import dev.arcmem.core.assembly.compaction.CompactionCompleted;
import dev.arcmem.core.assembly.compaction.CompactionConfig;
import dev.arcmem.core.assembly.compaction.SummaryResult;
import dev.arcmem.core.memory.engine.ArcMemEngine;
import dev.arcmem.core.memory.model.Authority;
import dev.arcmem.core.memory.model.MemoryUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CompactedContextProvider — Atomicity, Events, and OTEL (Sections 8.6, 8.7, 8.8)")
@ExtendWith(MockitoExtension.class)
class CompactedContextProviderAtomicityTest {

    private static final String CONTEXT_ID = "atomicity-ctx";

    @Mock
    private CompactionSummaryGenerator summaryGenerator;

    @Mock
    private ArcMemEngine arcMemEngine;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CompactedContextProvider provider;

    @BeforeEach
    void setUp() {
        provider = new CompactedContextProvider(List.of(), summaryGenerator, arcMemEngine, eventPublisher);
    }

    private CompactionConfig enabledConfig(double minMatchRatio, boolean eventsEnabled) {
        return new CompactionConfig(true, 0, 1, List.of(), minMatchRatio, 0, 1L, eventsEnabled);
    }

    private CompactionConfig enabledConfig(double minMatchRatio) {
        return enabledConfig(minMatchRatio, true);
    }

    @Nested
    @DisplayName("atomicity — validate before clear")
    class Atomicity {

        @Test
        @DisplayName("validation failure leaves messages intact — not cleared")
        void validationFailureLeavesMessagesIntact() {
            when(summaryGenerator.generateSummary(any(), anyString(), any(), anyInt(), any()))
                    .thenReturn(new SummaryResult("Unrelated tavern scene.", 0, false));
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(List.of(
                    MemoryUnit.withoutTrust("a-1", "The cursed blade corrupts its wielder",
                            700, Authority.RELIABLE, true, 0.9, 5)
            ));

            provider.addMessage(CONTEXT_ID, "Turn 1 message");
            provider.addMessage(CONTEXT_ID, "Turn 2 message");

            var result = provider.compact(CONTEXT_ID, enabledConfig(0.5), "test");

            assertThat(result.compactionApplied()).isFalse();
            assertThat(provider.getMessageCount(CONTEXT_ID)).isEqualTo(2);
        }

        @Test
        @DisplayName("fallback with poor content that fails validation leaves messages intact")
        void rollbackOnFallbackValidationFailure() {
            when(summaryGenerator.generateSummary(any(), anyString(), any(), anyInt(), any()))
                    .thenReturn(new SummaryResult("Some fallback text.", 2, true));
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(List.of(
                    MemoryUnit.withoutTrust("a-1", "Elminster lives in Shadowdale tower",
                            800, Authority.CANON, true, 1.0, 10)
            ));

            provider.addMessage(CONTEXT_ID, "Important message");

            var result = provider.compact(CONTEXT_ID, enabledConfig(0.5), "test");

            assertThat(result.compactionApplied()).isFalse();
            assertThat(result.fallbackUsed()).isTrue();
            assertThat(provider.getMessageCount(CONTEXT_ID)).isEqualTo(1);
        }

        @Test
        @DisplayName("successful validation clears messages and stores summary")
        void successfulCompactionClearsMessagesAndStoresSummary() {
            when(summaryGenerator.generateSummary(any(), anyString(), any(), anyInt(), any()))
                    .thenReturn(new SummaryResult("The dragon sleeps in the mountain cave.", 0, false));
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(List.of(
                    MemoryUnit.withoutTrust("a-1", "The dragon sleeps in the mountain cave",
                            700, Authority.RELIABLE, false, 0.9, 3)
            ));

            provider.addMessage(CONTEXT_ID, "Turn 1");
            provider.addMessage(CONTEXT_ID, "Turn 2");

            var result = provider.compact(CONTEXT_ID, enabledConfig(0.5), "test");

            assertThat(result.compactionApplied()).isTrue();
            assertThat(provider.getMessageCount(CONTEXT_ID)).isZero();
            assertThat(provider.getSummaries(CONTEXT_ID)).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("CompactionResult fields")
    class ResultFields {

        @Test
        @DisplayName("compactionApplied is true on success")
        void compactionAppliedTrueOnSuccess() {
            when(summaryGenerator.generateSummary(any(), anyString(), any(), anyInt(), any()))
                    .thenReturn(new SummaryResult("Summary.", 0, false));
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(List.of());

            provider.addMessage(CONTEXT_ID, "msg");
            var result = provider.compact(CONTEXT_ID, enabledConfig(0.5), "test");

            assertThat(result.compactionApplied()).isTrue();
        }

        @Test
        @DisplayName("compactionApplied is false on validation failure")
        void compactionAppliedFalseOnValidationFailure() {
            when(summaryGenerator.generateSummary(any(), anyString(), any(), anyInt(), any()))
                    .thenReturn(new SummaryResult("Unrelated.", 0, false));
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(List.of(
                    MemoryUnit.withoutTrust("a-1", "The cursed blade corrupts wielders",
                            700, Authority.RELIABLE, false, 0.9, 3)
            ));

            provider.addMessage(CONTEXT_ID, "msg");
            var result = provider.compact(CONTEXT_ID, enabledConfig(0.5), "test");

            assertThat(result.compactionApplied()).isFalse();
            assertThat(result.lossEvents()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("event publishing")
    class EventPublishing {

        @Test
        @DisplayName("event published on successful compaction")
        void eventPublishedOnSuccess() {
            when(summaryGenerator.generateSummary(any(), anyString(), any(), anyInt(), any()))
                    .thenReturn(new SummaryResult("Summary.", 0, false));
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(List.of());

            provider.addMessage(CONTEXT_ID, "msg");
            provider.compact(CONTEXT_ID, enabledConfig(0.5), "test");

            var captor = ArgumentCaptor.forClass(CompactionCompleted.class);
            verify(eventPublisher).publishEvent(captor.capture());

            var event = captor.getValue();
            assertThat(event.getContextId()).isEqualTo(CONTEXT_ID);
            assertThat(event.isCompactionApplied()).isTrue();
        }

        @Test
        @DisplayName("event published on fallback success with fallbackUsed=true")
        void eventPublishedOnFallbackSuccess() {
            when(summaryGenerator.generateSummary(any(), anyString(), any(), anyInt(), any()))
                    .thenReturn(new SummaryResult("Fallback text.", 2, true));
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(List.of());

            provider.addMessage(CONTEXT_ID, "msg");
            provider.compact(CONTEXT_ID, enabledConfig(0.5), "test");

            var captor = ArgumentCaptor.forClass(CompactionCompleted.class);
            verify(eventPublisher).publishEvent(captor.capture());

            var event = captor.getValue();
            assertThat(event.isFallbackUsed()).isTrue();
            assertThat(event.getRetryCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("no event published on validation failure (rollback)")
        void noEventOnRollback() {
            when(summaryGenerator.generateSummary(any(), anyString(), any(), anyInt(), any()))
                    .thenReturn(new SummaryResult("Unrelated text.", 0, false));
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(List.of(
                    MemoryUnit.withoutTrust("a-1", "The dragon guards golden treasure",
                            700, Authority.RELIABLE, false, 0.9, 3)
            ));

            provider.addMessage(CONTEXT_ID, "msg");
            provider.compact(CONTEXT_ID, enabledConfig(0.5), "test");

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("no event published when eventsEnabled=false even on success")
        void noEventWhenEventsDisabled() {
            when(summaryGenerator.generateSummary(any(), anyString(), any(), anyInt(), any()))
                    .thenReturn(new SummaryResult("Summary.", 0, false));
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(List.of());

            provider.addMessage(CONTEXT_ID, "msg");
            provider.compact(CONTEXT_ID, enabledConfig(0.5, false), "test");

            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("OTEL span attributes")
    class OtelAttributes {

        @Test
        @DisplayName("no OTEL attributes set when compact() is never called")
        void noOtelAttributesWhenNoCompaction() {
            provider.addMessage(CONTEXT_ID, "msg1");
            provider.addMessage(CONTEXT_ID, "msg2");

            verify(summaryGenerator, never()).generateSummary(any(), anyString(), any(), anyInt(), any());
        }

        @Test
        @DisplayName("compaction does not throw when Span.current() returns noop span")
        void noNpeWithNoopSpan() {
            when(summaryGenerator.generateSummary(any(), anyString(), any(), anyInt(), any()))
                    .thenReturn(new SummaryResult("Summary.", 0, false));
            when(arcMemEngine.inject(CONTEXT_ID)).thenReturn(List.of());

            provider.addMessage(CONTEXT_ID, "msg");
            var result = provider.compact(CONTEXT_ID, enabledConfig(0.5), "test_trigger");

            assertThat(result.triggerReason()).isEqualTo("test_trigger");
            assertThat(result.compactionApplied()).isTrue();
        }
    }
}
