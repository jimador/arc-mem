package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.CompliancePolicy;
import dev.dunnam.diceanchors.prompt.PromptTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Applies token-budget constraints to anchor prompt assembly while preserving
 * mandatory preamble/protocol text and all CANON anchors.
 */
public class PromptBudgetEnforcer {

    private static final Logger logger = LoggerFactory.getLogger(PromptBudgetEnforcer.class);

    private static final String MANDATORY_OVERHEAD =
            PromptTemplates.load("prompts/anchors-reference-overhead.jinja");

    public BudgetResult enforce(List<Anchor> anchors,
                                int tokenBudget,
                                TokenCounter counter,
                                CompliancePolicy policy) {
        if (anchors == null || anchors.isEmpty()) {
            var baseTokens = counter.estimate(MANDATORY_OVERHEAD);
            return new BudgetResult(List.of(), List.of(), baseTokens, baseTokens > tokenBudget);
        }
        if (tokenBudget <= 0) {
            return new BudgetResult(List.copyOf(anchors), List.of(), estimateTotal(counter, anchors), false);
        }

        var included = new ArrayList<>(anchors);
        var excluded = new ArrayList<Anchor>();

        var estimated = estimateTotal(counter, included);
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
                    .sorted(Comparator.comparingInt(Anchor::rank))
                    .toList();
            for (var candidate : candidates) {
                if (estimated <= tokenBudget) {
                    break;
                }
                included.remove(candidate);
                excluded.add(candidate);
                estimated = estimateTotal(counter, included);
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

    private int estimateTotal(TokenCounter counter, List<Anchor> anchors) {
        var tokens = counter.estimate(MANDATORY_OVERHEAD);
        for (var anchor : anchors) {
            tokens += counter.estimate(anchor.text()) + counter.estimate(" (rank: 999)");
        }
        return tokens;
    }
}
