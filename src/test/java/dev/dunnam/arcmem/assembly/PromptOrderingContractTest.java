package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.CompliancePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contract test validating the ordering of authority-tiered compliance blocks
 * in assembled prompts produced by {@link AnchorsLlmReference}.
 * <p>
 * Prompt ordering matters because LLMs attend more strongly to earlier context.
 * Authority tiers MUST appear in descending order (CANON first, PROVISIONAL last)
 * so that the highest-authority facts receive the strongest positional grounding.
 * <p>
 * The compliance framework and verification protocol are injected by the outer
 * system prompt template, not this anchor-tier fragment.
 */
@DisplayName("PromptOrderingContractTest")
class PromptOrderingContractTest {

    private static final String CONTEXT_ID = "test-ctx";
    private static final int BUDGET = 20;

    private static Anchor anchor(String id, String text, int rank, Authority authority) {
        return Anchor.withoutTrust(id, text, rank, authority, false, 0.9, 0);
    }

    private static String assemblePrompt(List<Anchor> anchors, CompliancePolicy policy) {
        var engine = mock(AnchorEngine.class);
        when(engine.inject(CONTEXT_ID)).thenReturn(anchors);
        var ref = new AnchorsLlmReference(engine, CONTEXT_ID, BUDGET, policy);
        return ref.getContent();
    }

    /**
     * Asserts that {@code earlier} appears before {@code later} in {@code content},
     * providing a clear diagnostic message on failure.
     */
    private static void assertOrderedBefore(String content, String earlier, String later) {
        var earlierIdx = content.indexOf(earlier);
        var laterIdx = content.indexOf(later);
        assertThat(earlierIdx)
                .as("Expected '%s' to be present in prompt", earlier)
                .isGreaterThanOrEqualTo(0);
        assertThat(laterIdx)
                .as("Expected '%s' to be present in prompt", later)
                .isGreaterThanOrEqualTo(0);
        assertThat(earlierIdx)
                .as("Expected '%s' (at %d) to appear before '%s' (at %d)",
                        earlier, earlierIdx, later, laterIdx)
                .isLessThan(laterIdx);
    }

    @Nested
    @DisplayName("AuthorityTieredCompliancePolicy")
    class TieredPolicy {

        private final CompliancePolicy policy = CompliancePolicy.tiered();

        @Test
        @DisplayName("authority tiers appear in descending order: CANON > RELIABLE > UNRELIABLE > PROVISIONAL")
        void getContentAllTiersDescendingOrder() {
            var anchors = List.of(
                    anchor("a1", "Canon fact", 900, Authority.CANON),
                    anchor("a2", "Reliable fact", 700, Authority.RELIABLE),
                    anchor("a3", "Unreliable fact", 400, Authority.UNRELIABLE),
                    anchor("a4", "Provisional fact", 200, Authority.PROVISIONAL)
            );

            var content = assemblePrompt(anchors, policy);

            assertOrderedBefore(content, "CANON FACTS", "RELIABLE FACTS");
            assertOrderedBefore(content, "RELIABLE FACTS", "UNRELIABLE FACTS");
            assertOrderedBefore(content, "UNRELIABLE FACTS", "PROVISIONAL FACTS");
        }

        @Test
        @DisplayName("empty tiers are skipped without breaking order")
        void getContentEmptyTiersSkippedOrderPreserved() {
            var anchors = List.of(
                    anchor("a1", "Canon fact", 900, Authority.CANON),
                    anchor("a4", "Provisional fact", 200, Authority.PROVISIONAL)
            );

            var content = assemblePrompt(anchors, policy);

            assertOrderedBefore(content, "CANON FACTS", "PROVISIONAL FACTS");
            assertThat(content).doesNotContain("RELIABLE FACTS");
            assertThat(content).doesNotContain("UNRELIABLE FACTS");
        }

        @Test
        @DisplayName("tier fragment excludes verification protocol")
        void getContentExcludesVerificationProtocol() {
            var anchors = List.of(
                    anchor("a1", "Canon fact", 900, Authority.CANON),
                    anchor("a2", "Reliable fact", 700, Authority.RELIABLE),
                    anchor("a3", "Unreliable fact", 400, Authority.UNRELIABLE),
                    anchor("a4", "Provisional fact", 200, Authority.PROVISIONAL)
            );

            var content = assemblePrompt(anchors, policy);

            assertThat(content).doesNotContain("Verification Protocol");
        }

        @Test
        @DisplayName("tiered policy applies correct compliance language per authority")
        void getContentTieredComplianceLanguage() {
            var anchors = List.of(
                    anchor("a1", "Canon fact", 900, Authority.CANON),
                    anchor("a2", "Unreliable fact", 400, Authority.UNRELIABLE),
                    anchor("a3", "Provisional fact", 200, Authority.PROVISIONAL)
            );

            var content = assemblePrompt(anchors, policy);

            // CANON -> STRICT -> "MUST be preserved"
            assertThat(content).contains("CANON FACTS (MUST be preserved");
            // UNRELIABLE -> MODERATE -> "SHOULD be considered"
            assertThat(content).contains("UNRELIABLE FACTS (SHOULD be considered");
            // PROVISIONAL -> PERMISSIVE -> "MAY be reconsidered"
            assertThat(content).contains("PROVISIONAL FACTS (MAY be reconsidered");
        }
    }

    @Nested
    @DisplayName("DefaultCompliancePolicy")
    class FlatPolicy {

        private final CompliancePolicy policy = CompliancePolicy.flat();

        @Test
        @DisplayName("all tiers use STRICT compliance language")
        void getContentAllTiersStrictCompliance() {
            var anchors = List.of(
                    anchor("a1", "Canon fact", 900, Authority.CANON),
                    anchor("a2", "Reliable fact", 700, Authority.RELIABLE),
                    anchor("a3", "Unreliable fact", 400, Authority.UNRELIABLE),
                    anchor("a4", "Provisional fact", 200, Authority.PROVISIONAL)
            );

            var content = assemblePrompt(anchors, policy);

            assertThat(content).contains("CANON FACTS (MUST be preserved");
            assertThat(content).contains("RELIABLE FACTS (MUST be preserved");
            assertThat(content).contains("UNRELIABLE FACTS (MUST be preserved");
            assertThat(content).contains("PROVISIONAL FACTS (MUST be preserved");
        }

        @Test
        @DisplayName("authority tier ordering preserved with default policy")
        void getContentDefaultPolicyTierOrderPreserved() {
            var anchors = List.of(
                    anchor("a1", "Canon fact", 900, Authority.CANON),
                    anchor("a2", "Reliable fact", 700, Authority.RELIABLE),
                    anchor("a3", "Unreliable fact", 400, Authority.UNRELIABLE),
                    anchor("a4", "Provisional fact", 200, Authority.PROVISIONAL)
            );

            var content = assemblePrompt(anchors, policy);

            assertOrderedBefore(content, "CANON FACTS", "RELIABLE FACTS");
            assertOrderedBefore(content, "RELIABLE FACTS", "UNRELIABLE FACTS");
            assertOrderedBefore(content, "UNRELIABLE FACTS", "PROVISIONAL FACTS");
        }

        @Test
        @DisplayName("tier fragment excludes verification protocol")
        void getContentExcludesVerificationProtocol() {
            var anchors = List.of(
                    anchor("a1", "Canon fact", 900, Authority.CANON),
                    anchor("a4", "Provisional fact", 200, Authority.PROVISIONAL)
            );

            var content = assemblePrompt(anchors, policy);

            assertThat(content).doesNotContain("Verification Protocol");
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("empty anchor list returns empty string")
        void getContentNoAnchorsReturnsEmpty() {
            var content = assemblePrompt(List.of(), CompliancePolicy.flat());
            assertThat(content).isEmpty();
        }

        @Test
        @DisplayName("single-tier prompt excludes Verification Protocol")
        void getContentSingleTierExcludesVerification() {
            var anchors = List.of(
                    anchor("a1", "Only reliable fact", 700, Authority.RELIABLE)
            );

            var content = assemblePrompt(anchors, CompliancePolicy.tiered());

            assertThat(content).contains("RELIABLE FACTS");
            assertThat(content).doesNotContain("Verification Protocol");
            assertThat(content).doesNotContain("CANON FACTS");
            assertThat(content).doesNotContain("UNRELIABLE FACTS");
            assertThat(content).doesNotContain("PROVISIONAL FACTS");
        }

        @Test
        @DisplayName("renders anchor text and rank values")
        void getContentRendersAnchorTextAndRankValues() {
            var anchors = List.of(
                    anchor("a1", "The East Gate is breached", 650, Authority.RELIABLE)
            );

            var content = assemblePrompt(anchors, CompliancePolicy.tiered());

            assertThat(content).contains("The East Gate is breached");
            assertThat(content).contains("(rank: 650)");
            assertThat(content).doesNotContain("1.  (rank: )");
        }
    }
}
