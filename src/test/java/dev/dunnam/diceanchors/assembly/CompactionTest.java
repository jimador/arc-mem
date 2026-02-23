package dev.dunnam.diceanchors.assembly;

import com.embabel.dice.proposition.Proposition;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Compaction Pipeline")
@ExtendWith(MockitoExtension.class)
class CompactionTest {

    private static final String CONTEXT_ID = "test-ctx-1";

    @Nested
    @DisplayName("CompactedContextProvider")
    class CompactedContextProviderTests {

        @Mock
        private SimSummaryGenerator summaryGenerator;

        @Mock
        private AnchorEngine anchorEngine;

        private CompactedContextProvider provider;

        @BeforeEach
        void setUp() {
            provider = new CompactedContextProvider(List.of(), summaryGenerator, anchorEngine, event -> {});
        }

        @Nested
        @DisplayName("shouldCompact")
        class ShouldCompact {

            @Test
            @DisplayName("returns false when compaction is disabled")
            void shouldCompactDisabledReturnsFalse() {
                var config = CompactionConfig.disabled();
                provider.addMessage(CONTEXT_ID, "Hello world");

                assertThat(provider.shouldCompact(CONTEXT_ID, config)).isFalse();
            }

            @Test
            @DisplayName("returns true when message count reaches threshold")
            void shouldCompactMessageThresholdReachedReturnsTrue() {
                var config = new CompactionConfig(true, 0, 3, List.of(), 0.5, 2, 1000L, true);
                provider.addMessage(CONTEXT_ID, "Message 1");
                provider.addMessage(CONTEXT_ID, "Message 2");
                provider.addMessage(CONTEXT_ID, "Message 3");

                assertThat(provider.shouldCompact(CONTEXT_ID, config)).isTrue();
            }

            @Test
            @DisplayName("returns false when message count is below threshold")
            void shouldCompactMessageCountBelowThresholdReturnsFalse() {
                var config = new CompactionConfig(true, 0, 5, List.of(), 0.5, 2, 1000L, true);
                provider.addMessage(CONTEXT_ID, "Message 1");
                provider.addMessage(CONTEXT_ID, "Message 2");

                assertThat(provider.shouldCompact(CONTEXT_ID, config)).isFalse();
            }

            @Test
            @DisplayName("returns true when token estimate reaches threshold")
            void shouldCompactTokenThresholdReachedReturnsTrue() {
                // 4 chars per token heuristic, so 40 chars = 10 tokens
                var config = new CompactionConfig(true, 10, 0, List.of(), 0.5, 2, 1000L, true);
                provider.addMessage(CONTEXT_ID, "a]".repeat(20)); // 40 chars = 10 tokens

                assertThat(provider.shouldCompact(CONTEXT_ID, config)).isTrue();
            }

            @Test
            @DisplayName("returns false when token estimate is below threshold")
            void shouldCompactTokensBelowThresholdReturnsFalse() {
                var config = new CompactionConfig(true, 100, 0, List.of(), 0.5, 2, 1000L, true);
                provider.addMessage(CONTEXT_ID, "short");

                assertThat(provider.shouldCompact(CONTEXT_ID, config)).isFalse();
            }

            @Test
            @DisplayName("returns false for unknown context with no messages")
            void shouldCompactNoMessagesReturnsFalse() {
                var config = new CompactionConfig(true, 10, 3, List.of(), 0.5, 2, 1000L, true);

                assertThat(provider.shouldCompact("unknown-ctx", config)).isFalse();
            }
        }

        @Nested
        @DisplayName("isForcedTurn")
        class IsForcedTurn {

            @Test
            @DisplayName("returns true when current turn is in forceAtTurns list")
            void isForcedTurnMatchingTurnReturnsTrue() {
                var config = new CompactionConfig(true, 0, 0, List.of(3, 7, 12), 0.5, 2, 1000L, true);

                assertThat(provider.isForcedTurn(3, config)).isTrue();
                assertThat(provider.isForcedTurn(7, config)).isTrue();
                assertThat(provider.isForcedTurn(12, config)).isTrue();
            }

            @Test
            @DisplayName("returns false when current turn is not in forceAtTurns list")
            void isForcedTurnNonMatchingTurnReturnsFalse() {
                var config = new CompactionConfig(true, 0, 0, List.of(3, 7, 12), 0.5, 2, 1000L, true);

                assertThat(provider.isForcedTurn(1, config)).isFalse();
                assertThat(provider.isForcedTurn(5, config)).isFalse();
            }

            @Test
            @DisplayName("returns false when compaction is disabled")
            void isForcedTurnDisabledReturnsFalse() {
                var config = new CompactionConfig(false, 0, 0, List.of(3), 0.5, 2, 1000L, true);

                assertThat(provider.isForcedTurn(3, config)).isFalse();
            }
        }

        @Nested
        @DisplayName("compact")
        class Compact {

            @Test
            @DisplayName("generates summary and clears message history")
            void compactClearsMessagesAndStoresSummary() {
                when(summaryGenerator.generateSummary(any(), anyString(), any(), anyInt(), any()))
                        .thenReturn(new SummaryResult("Summary of the session.", 0, false));

                provider.addMessage(CONTEXT_ID, "Turn 1 message");
                provider.addMessage(CONTEXT_ID, "Turn 2 message");
                var config = new CompactionConfig(true, 0, 2, List.of(), 0.5, 2, 1000L, true);

                var result = provider.compact(CONTEXT_ID, config, "message_threshold");

                assertThat(result.triggerReason()).isEqualTo("message_threshold");
                assertThat(result.summary()).isEqualTo("Summary of the session.");
                assertThat(result.tokensBefore()).isGreaterThan(0);
                assertThat(result.tokensAfter()).isGreaterThan(0);
                assertThat(result.tokensBefore()).isGreaterThan(result.tokensAfter());
                assertThat(provider.getMessageCount(CONTEXT_ID)).isZero();
            }

            @Test
            @DisplayName("stores summary retrievable via getSummaries")
            void compactStoresSummaryRetrievableAfterwards() {
                when(summaryGenerator.generateSummary(any(), anyString(), any(), anyInt(), any()))
                        .thenReturn(new SummaryResult("The party defeated the dragon.", 0, false));

                provider.addMessage(CONTEXT_ID, "Combat round 1");
                var config = new CompactionConfig(true, 0, 1, List.of(), 0.5, 2, 1000L, true);
                provider.compact(CONTEXT_ID, config, "forced_turn");

                var summaries = provider.getSummaries(CONTEXT_ID);
                assertThat(summaries).hasSize(1);
                assertThat(summaries.getFirst()).isEqualTo("The party defeated the dragon.");
            }

            @Test
            @DisplayName("auto-detects token_threshold trigger reason")
            void compactAutoDetectsTokenThresholdReason() {
                when(summaryGenerator.generateSummary(any(), anyString(), any(), anyInt(), any()))
                        .thenReturn(new SummaryResult("Short.", 0, false));

                // 80 chars = 20 tokens, threshold is 10
                provider.addMessage(CONTEXT_ID, "x".repeat(80));
                var config = new CompactionConfig(true, 10, 0, List.of(), 0.5, 2, 1000L, true);

                var result = provider.compact(CONTEXT_ID, config);

                assertThat(result.triggerReason()).isEqualTo("token_threshold");
            }

            @Test
            @DisplayName("auto-detects message_threshold trigger reason")
            void compactAutoDetectsMessageThresholdReason() {
                when(summaryGenerator.generateSummary(any(), anyString(), any(), anyInt(), any()))
                        .thenReturn(new SummaryResult("Short.", 0, false));

                provider.addMessage(CONTEXT_ID, "msg1");
                provider.addMessage(CONTEXT_ID, "msg2");
                provider.addMessage(CONTEXT_ID, "msg3");
                var config = new CompactionConfig(true, 0, 3, List.of(), 0.5, 2, 1000L, true);

                var result = provider.compact(CONTEXT_ID, config);

                assertThat(result.triggerReason()).isEqualTo("message_threshold");
            }

            @Test
            @DisplayName("auto-detects forced_turn trigger reason when no threshold met")
            void compactAutoDetectsForcedTurnReason() {
                when(summaryGenerator.generateSummary(any(), anyString(), any(), anyInt(), any()))
                        .thenReturn(new SummaryResult("Short.", 0, false));

                provider.addMessage(CONTEXT_ID, "msg");
                var config = new CompactionConfig(true, 99999, 99999, List.of(1), 0.5, 2, 1000L, true);

                var result = provider.compact(CONTEXT_ID, config);

                assertThat(result.triggerReason()).isEqualTo("forced_turn");
            }

            @Test
            @DisplayName("collects protected content IDs from all providers")
            void compactCollectsProtectedContentIds() {
                var protector = mock(ProtectedContentProvider.class);
                when(protector.getProtectedContent(CONTEXT_ID)).thenReturn(List.of(
                        new ProtectedContent("anchor-1", "The sword is cursed", 700, "anchor"),
                        new ProtectedContent("anchor-2", "Elf king is hostile", 500, "anchor")
                ));
                when(summaryGenerator.generateSummary(any(), anyString(), any(), anyInt(), any()))
                        .thenReturn(new SummaryResult("Summary.", 0, false));

                var providerWithProtectors = new CompactedContextProvider(
                        List.of(protector), summaryGenerator, anchorEngine, event -> {});
                providerWithProtectors.addMessage(CONTEXT_ID, "msg");
                var config = new CompactionConfig(true, 0, 1, List.of(), 0.5, 2, 1000L, true);

                var result = providerWithProtectors.compact(CONTEXT_ID, config, "test");

                assertThat(result.protectedContentIds()).containsExactly("anchor-1", "anchor-2");
            }
        }

        @Nested
        @DisplayName("clearContext")
        class ClearContext {

            @Test
            @DisplayName("removes messages and summaries for the context")
            void clearContextRemovesAllState() {
                when(summaryGenerator.generateSummary(any(), anyString(), any(), anyInt(), any()))
                        .thenReturn(new SummaryResult("Summary.", 0, false));

                provider.addMessage(CONTEXT_ID, "msg");
                var config = new CompactionConfig(true, 0, 1, List.of(), 0.5, 2, 1000L, true);
                provider.compact(CONTEXT_ID, config, "test");

                provider.clearContext(CONTEXT_ID);

                assertThat(provider.getMessageCount(CONTEXT_ID)).isZero();
                assertThat(provider.getSummaries(CONTEXT_ID)).isEmpty();
            }
        }

        @Nested
        @DisplayName("getEstimatedTokens")
        class GetEstimatedTokens {

            @Test
            @DisplayName("estimates tokens at 4 chars per token")
            void getEstimatedTokensCalculatesCorrectly() {
                // 20 chars = 5 tokens
                provider.addMessage(CONTEXT_ID, "12345678901234567890");

                assertThat(provider.getEstimatedTokens(CONTEXT_ID)).isEqualTo(5);
            }

            @Test
            @DisplayName("returns zero for unknown context")
            void getEstimatedTokensUnknownContextReturnsZero() {
                assertThat(provider.getEstimatedTokens("unknown")).isZero();
            }
        }
    }

    @Nested
    @DisplayName("AnchorContentProtector")
    class AnchorContentProtectorTests {

        @Mock
        private AnchorEngine anchorEngine;

        private AnchorContentProtector protector;

        @BeforeEach
        void setUp() {
            protector = new AnchorContentProtector(anchorEngine);
        }

        @Test
        @DisplayName("priority equals anchor rank")
        void getProtectedContentPriorityEqualsRank() {
            var anchor = Anchor.withoutTrust(
                    "a-1", "The dragon is sleeping", 750, Authority.RELIABLE, false, 0.9, 3);
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(List.of(anchor));

            var result = protector.getProtectedContent(CONTEXT_ID);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().priority()).isEqualTo(750);
        }

        @Test
        @DisplayName("returns id and text from anchor")
        void getProtectedContentReturnsIdAndText() {
            var anchor = Anchor.withoutTrust(
                    "a-42", "Gandalf is a wizard", 500, Authority.CANON, true, 0.95, 5);
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(List.of(anchor));

            var result = protector.getProtectedContent(CONTEXT_ID);

            assertThat(result).hasSize(1);
            var pc = result.getFirst();
            assertThat(pc.id()).isEqualTo("a-42");
            assertThat(pc.text()).isEqualTo("Gandalf is a wizard");
        }

        @Test
        @DisplayName("returns multiple protected items for multiple anchors")
        void getProtectedContentMultipleAnchorsReturnsMultipleItems() {
            var anchors = List.of(
                    Anchor.withoutTrust("a-1", "Fact one", 800, Authority.CANON, true, 1.0, 10),
                    Anchor.withoutTrust("a-2", "Fact two", 300, Authority.PROVISIONAL, false, 0.6, 0)
            );
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(anchors);

            var result = protector.getProtectedContent(CONTEXT_ID);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(ProtectedContent::priority).containsExactly(800, 300);
        }

        @Test
        @DisplayName("returns empty list when no anchors exist")
        void getProtectedContentNoAnchorsReturnsEmpty() {
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(List.of());

            var result = protector.getProtectedContent(CONTEXT_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("reason includes authority name and rank")
        void getProtectedContentReasonIncludesAuthorityAndRank() {
            var anchor = Anchor.withoutTrust(
                    "a-1", "The blade is cursed", 600, Authority.UNRELIABLE, false, 0.7, 1);
            when(anchorEngine.inject(CONTEXT_ID)).thenReturn(List.of(anchor));

            var result = protector.getProtectedContent(CONTEXT_ID);

            assertThat(result.getFirst().reason()).contains("UNRELIABLE").contains("600");
        }
    }

    @Nested
    @DisplayName("PropositionContentProtector")
    class PropositionContentProtectorTests {

        @Mock
        private AnchorRepository repository;

        private PropositionContentProtector protector;

        @BeforeEach
        void setUp() {
            protector = new PropositionContentProtector(repository);
        }

        @Test
        @DisplayName("priority is confidence times 100")
        void getProtectedContentPriorityIsConfidenceTimes100() {
            var prop = mock(Proposition.class);
            when(prop.getId()).thenReturn("p-1");
            when(prop.getText()).thenReturn("The tavern is in Waterdeep");
            when(prop.getConfidence()).thenReturn(0.85);
            when(repository.findByContextIdValue(CONTEXT_ID)).thenReturn(List.of(prop));

            var result = protector.getProtectedContent(CONTEXT_ID);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().priority()).isEqualTo(85);
        }

        @Test
        @DisplayName("filters out propositions with zero confidence")
        void getProtectedContentZeroConfidenceFiltered() {
            var prop = mock(Proposition.class);
            when(prop.getConfidence()).thenReturn(0.0);
            when(repository.findByContextIdValue(CONTEXT_ID)).thenReturn(List.of(prop));

            var result = protector.getProtectedContent(CONTEXT_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns id and text from proposition")
        void getProtectedContentReturnsIdAndText() {
            var prop = mock(Proposition.class);
            when(prop.getId()).thenReturn("p-99");
            when(prop.getText()).thenReturn("Elminster is in Shadowdale");
            when(prop.getConfidence()).thenReturn(0.72);
            when(repository.findByContextIdValue(CONTEXT_ID)).thenReturn(List.of(prop));

            var result = protector.getProtectedContent(CONTEXT_ID);

            var pc = result.getFirst();
            assertThat(pc.id()).isEqualTo("p-99");
            assertThat(pc.text()).isEqualTo("Elminster is in Shadowdale");
        }

        @Test
        @DisplayName("handles multiple propositions with varying confidence")
        void getProtectedContentMultiplePropositionsReturnsAll() {
            var high = mock(Proposition.class);
            when(high.getId()).thenReturn("p-1");
            when(high.getText()).thenReturn("High confidence fact");
            when(high.getConfidence()).thenReturn(0.95);

            var low = mock(Proposition.class);
            when(low.getId()).thenReturn("p-2");
            when(low.getText()).thenReturn("Low confidence fact");
            when(low.getConfidence()).thenReturn(0.3);

            var zero = mock(Proposition.class);
            when(zero.getConfidence()).thenReturn(0.0);

            when(repository.findByContextIdValue(CONTEXT_ID)).thenReturn(List.of(high, low, zero));

            var result = protector.getProtectedContent(CONTEXT_ID);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(ProtectedContent::priority).containsExactly(95, 30);
        }

        @Test
        @DisplayName("returns empty list when no propositions exist")
        void getProtectedContentNoPropositionsReturnsEmpty() {
            when(repository.findByContextIdValue(CONTEXT_ID)).thenReturn(List.of());

            var result = protector.getProtectedContent(CONTEXT_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("reason includes formatted confidence value")
        void getProtectedContentReasonIncludesConfidence() {
            var prop = mock(Proposition.class);
            when(prop.getId()).thenReturn("p-1");
            when(prop.getText()).thenReturn("fact");
            when(prop.getConfidence()).thenReturn(0.88);
            when(repository.findByContextIdValue(CONTEXT_ID)).thenReturn(List.of(prop));

            var result = protector.getProtectedContent(CONTEXT_ID);

            assertThat(result.getFirst().reason()).contains("0.88");
        }
    }

    @Nested
    @DisplayName("CompactionDriftEvaluator")
    class CompactionDriftEvaluatorTests {

        private CompactionDriftEvaluator evaluator;

        @BeforeEach
        void setUp() {
            evaluator = new CompactionDriftEvaluator();
        }

        @Test
        @DisplayName("detects loss when content is missing after compaction")
        void evaluateMissingContentReportsLoss() {
            var before = Set.of("a-1", "a-2", "a-3");
            var after = Set.of("a-1", "a-3");

            var losses = evaluator.evaluate(before, after);

            assertThat(losses).hasSize(1);
            assertThat(losses.getFirst().lostContentId()).isEqualTo("a-2");
        }

        @Test
        @DisplayName("reports no loss when all content preserved")
        void evaluateAllPreservedReportsNoLoss() {
            var ids = Set.of("a-1", "a-2", "a-3");

            var losses = evaluator.evaluate(ids, ids);

            assertThat(losses).isEmpty();
        }

        @Test
        @DisplayName("reports multiple losses when several items missing")
        void evaluateMultipleMissingReportsMultipleLosses() {
            var before = Set.of("a-1", "a-2", "a-3", "a-4");
            var after = Set.of("a-1");

            var losses = evaluator.evaluate(before, after);

            assertThat(losses).hasSize(3);
            assertThat(losses).extracting(CompactionDriftEvaluator.CompactionLoss::lostContentId)
                    .containsExactlyInAnyOrder("a-2", "a-3", "a-4");
        }

        @Test
        @DisplayName("handles empty before set with no losses")
        void evaluateEmptyBeforeReportsNoLoss() {
            var losses = evaluator.evaluate(Set.of(), Set.of("a-1"));

            assertThat(losses).isEmpty();
        }

        @Test
        @DisplayName("handles empty after set reporting all as lost")
        void evaluateEmptyAfterReportsAllLost() {
            var before = Set.of("a-1", "a-2");

            var losses = evaluator.evaluate(before, Set.of());

            assertThat(losses).hasSize(2);
        }

        @Test
        @DisplayName("loss text includes the content ID")
        void evaluateLossTextIncludesContentId() {
            var losses = evaluator.evaluate(Set.of("anchor-42"), Set.of());

            assertThat(losses.getFirst().lostText()).contains("anchor-42");
        }

        @Test
        @DisplayName("ignores new content present only in after set")
        void evaluateNewContentInAfterNotReportedAsLoss() {
            var before = Set.of("a-1");
            var after = Set.of("a-1", "a-new");

            var losses = evaluator.evaluate(before, after);

            assertThat(losses).isEmpty();
        }
    }

    @Nested
    @DisplayName("CompactionConfig")
    class CompactionConfigTests {

        @Test
        @DisplayName("disabled() returns config with enabled=false")
        void disabledReturnsDisabledConfig() {
            var config = CompactionConfig.disabled();

            assertThat(config.enabled()).isFalse();
            assertThat(config.tokenThreshold()).isZero();
            assertThat(config.messageThreshold()).isZero();
            assertThat(config.forceAtTurns()).isEmpty();
        }
    }

    @Nested
    @DisplayName("ProtectedContent")
    class ProtectedContentTests {

        @Test
        @DisplayName("record accessors return constructor values")
        void recordAccessorsReturnCorrectValues() {
            var pc = new ProtectedContent("id-1", "some text", 42, "a reason");

            assertThat(pc.id()).isEqualTo("id-1");
            assertThat(pc.text()).isEqualTo("some text");
            assertThat(pc.priority()).isEqualTo(42);
            assertThat(pc.reason()).isEqualTo("a reason");
        }
    }

    @Nested
    @DisplayName("CompactionResult")
    class CompactionResultTests {

        @Test
        @DisplayName("record accessors return constructor values")
        void recordAccessorsReturnCorrectValues() {
            var result = new CompactionResult(
                    "token_threshold", 500, 50, List.of("a-1", "a-2"), "Summary text.", 123L, List.of(),
                    true, 1, false);

            assertThat(result.triggerReason()).isEqualTo("token_threshold");
            assertThat(result.tokensBefore()).isEqualTo(500);
            assertThat(result.tokensAfter()).isEqualTo(50);
            assertThat(result.protectedContentIds()).containsExactly("a-1", "a-2");
            assertThat(result.summary()).isEqualTo("Summary text.");
            assertThat(result.durationMs()).isEqualTo(123L);
            assertThat(result.lossEvents()).isEmpty();
            assertThat(result.compactionApplied()).isTrue();
            assertThat(result.retryCount()).isEqualTo(1);
            assertThat(result.fallbackUsed()).isFalse();
        }
    }
}
