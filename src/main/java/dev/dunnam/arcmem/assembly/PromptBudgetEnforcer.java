package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.CompliancePolicy;
import dev.dunnam.diceanchors.prompt.PromptPathConstants;
import dev.dunnam.diceanchors.prompt.PromptTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Applies token-budget constraints to anchor prompt assembly while preserving
 * mandatory preamble/protocol text and all CANON anchors.
 * <p>
 * <strong>Budget algorithm:</strong> Given a list of candidate anchors and a token budget,
 * this enforcer iteratively drops anchors in drop-order until the estimated token count
 * falls within budget or no more droppable anchors remain.
 * <p>
 * <strong>Drop order</strong> (anchors dropped earliest are listed first):
 * <ol>
 *   <li>PROVISIONAL — lowest trust, highest eviction priority</li>
 *   <li>UNRELIABLE</li>
 *   <li>RELIABLE — dropped last among non-CANON anchors</li>
 *   <li>CANON — <em>never dropped</em>, always included regardless of budget</li>
 * </ol>
 * Within each authority tier, anchors are sorted by:
 * <ol>
 *   <li>{@code diceImportance} ascending — anchors with low DICE importance (0.0 default)
 *       are dropped before high-importance anchors</li>
 *   <li>{@code rank} ascending — within the same importance value, lower-ranked anchors
 *       are dropped first, preserving the original rank-based behavior</li>
 * </ol>
 * <p>
 * <strong>Mandatory overhead:</strong> The enforcer accounts for a fixed token cost
 * from the anchor reference preamble template
 * ({@link dev.dunnam.diceanchors.prompt.PromptPathConstants#ANCHORS_REFERENCE_OVERHEAD}).
 * This overhead is always counted against the budget, even when the anchor list is empty.
 */
public class PromptBudgetEnforcer {

    private static final Logger logger = LoggerFactory.getLogger(PromptBudgetEnforcer.class);

    private static final String MANDATORY_OVERHEAD =
            PromptTemplates.load(PromptPathConstants.ANCHORS_REFERENCE_OVERHEAD);

    /**
     * Enforce the token budget on a list of candidate anchors.
     * <p>
     * CANON anchors are always included. Non-CANON anchors are dropped in
     * priority order (PROVISIONAL first, then UNRELIABLE, then RELIABLE), with
     * low-importance anchors dropped before high-importance ones within each tier.
     * If the budget cannot be satisfied even after dropping all non-CANON anchors,
     * the result is returned with {@code budgetExceeded = true} and the full CANON
     * set still included.
     *
     * @param anchors     candidate anchors to fit within budget; MUST NOT be null
     * @param tokenBudget maximum allowed tokens; pass {@code <= 0} to skip enforcement
     * @param counter     token counter for estimation
     * @param policy      compliance policy (currently unused in budget math, reserved)
     * @return a {@link BudgetResult} where {@link BudgetResult#included()} are the anchors
     *         that fit within budget and {@link BudgetResult#excluded()} are those dropped
     */
    public BudgetResult enforce(List<Anchor> anchors,
                                int tokenBudget,
                                TokenCounter counter,
                                CompliancePolicy policy) {
        return enforce(anchors, tokenBudget, counter, policy, false);
    }

    /**
     * Enforce the token budget on a list of candidate anchors with adaptive footprint option.
     * <p>
     * When {@code adaptiveFootprintEnabled} is true, per-anchor token estimation uses
     * authority-specific templates, resulting in different costs per authority level
     * (CANON anchors cost fewer tokens, PROVISIONAL anchors cost more).
     * <p>
     * When {@code adaptiveFootprintEnabled} is false, all anchors are estimated uniformly.
     *
     * @param anchors                  candidate anchors to fit within budget; MUST NOT be null
     * @param tokenBudget              maximum allowed tokens; pass {@code <= 0} to skip enforcement
     * @param counter                  token counter for estimation
     * @param policy                   compliance policy (currently unused in budget math, reserved)
     * @param adaptiveFootprintEnabled whether to use authority-specific template estimation
     * @return a {@link BudgetResult} where {@link BudgetResult#included()} are the anchors
     *         that fit within budget and {@link BudgetResult#excluded()} are those dropped
     */
    public BudgetResult enforce(List<Anchor> anchors,
                                int tokenBudget,
                                TokenCounter counter,
                                CompliancePolicy policy,
                                boolean adaptiveFootprintEnabled) {
        if (anchors == null || anchors.isEmpty()) {
            var baseTokens = counter.estimate(MANDATORY_OVERHEAD);
            return new BudgetResult(List.of(), List.of(), baseTokens, baseTokens > tokenBudget);
        }
        if (tokenBudget <= 0) {
            return new BudgetResult(List.copyOf(anchors), List.of(), estimateTotal(counter, anchors, adaptiveFootprintEnabled), false);
        }

        var included = new ArrayList<>(anchors);
        var excluded = new ArrayList<Anchor>();

        var estimated = estimateTotal(counter, included, adaptiveFootprintEnabled);
        var budgetExceeded = estimated > tokenBudget;
        if (!budgetExceeded) {
            return new BudgetResult(List.copyOf(included), List.of(), estimated, false);
        }

        for (var authority : List.of(Authority.PROVISIONAL, Authority.UNRELIABLE, Authority.RELIABLE)) {
            if (estimated <= tokenBudget) {
                break;
            }
            var candidates = included.stream()
                    .filter(anchor -> anchor.authority() == authority)
                    .sorted(Comparator.comparingInt((Anchor a) -> a.memoryTier().ordinal())
                            .thenComparingDouble(Anchor::diceImportance)
                            .thenComparingInt(Anchor::rank))
                    .toList();
            for (var candidate : candidates) {
                if (estimated <= tokenBudget) {
                    break;
                }
                included.remove(candidate);
                excluded.add(candidate);
                estimated = estimateTotal(counter, included, adaptiveFootprintEnabled);
            }
        }

        var canonOnlyExceeded = included.stream().allMatch(anchor -> anchor.authority() == Authority.CANON)
                && estimated > tokenBudget;
        if (!excluded.isEmpty() || canonOnlyExceeded) {
            logger.info("Prompt token budget enforcement: budget={}, estimated={}, included={}, excluded={}, canonOnlyExceeded={}",
                    tokenBudget, estimated, included.size(), excluded.size(), canonOnlyExceeded);
        }

        return new BudgetResult(
                List.copyOf(included),
                List.copyOf(excluded),
                estimated,
                estimated > tokenBudget || !excluded.isEmpty());
    }

    private int estimateTotal(TokenCounter counter, List<Anchor> anchors, boolean adaptiveFootprintEnabled) {
        var tokens = counter.estimate(MANDATORY_OVERHEAD);
        for (var anchor : anchors) {
            tokens += estimateAnchorTokens(counter, anchor, adaptiveFootprintEnabled);
        }
        return tokens;
    }

    private int estimateAnchorTokens(TokenCounter counter, Anchor anchor, boolean adaptiveFootprintEnabled) {
        if (!adaptiveFootprintEnabled) {
            return counter.estimate(anchor.text()) + counter.estimate(" (rank: 999)");
        }
        var template = templateForAuthority(anchor.authority());
        var rendered = PromptTemplates.render(template, Map.of(
                "index", 1,
                "text", anchor.text(),
                "rank", anchor.rank()));
        return counter.estimate(rendered);
    }

    private String templateForAuthority(Authority authority) {
        return switch (authority) {
            case CANON -> PromptPathConstants.ANCHOR_TEMPLATE_CANON;
            case RELIABLE -> PromptPathConstants.ANCHOR_TEMPLATE_RELIABLE;
            case UNRELIABLE -> PromptPathConstants.ANCHOR_TEMPLATE_UNRELIABLE;
            case PROVISIONAL -> PromptPathConstants.ANCHOR_TEMPLATE_PROVISIONAL;
        };
    }
}
