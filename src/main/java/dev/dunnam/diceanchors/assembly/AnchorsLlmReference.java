package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.CompliancePolicy;
import dev.dunnam.diceanchors.prompt.PromptTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides ranked anchor context for injection into the system prompt.
 * Queries AnchorEngine for active anchors within budget, formats them
 * as a structured context block with authority-tiered compliance.
 */
public class AnchorsLlmReference {

    private static final Logger logger = LoggerFactory.getLogger(AnchorsLlmReference.class);

    private final AnchorEngine engine;
    private final String contextId;
    private final int budget;
    private final CompliancePolicy compliancePolicy;
    private final int tokenBudget;
    private final TokenCounter tokenCounter;
    private final PromptBudgetEnforcer budgetEnforcer;

    private List<Anchor> cachedAnchors;
    private List<Anchor> selectedAnchors;
    private BudgetResult lastBudgetResult;

    public AnchorsLlmReference(AnchorEngine engine, String contextId, int budget,
                               CompliancePolicy compliancePolicy) {
        this(engine, contextId, budget, compliancePolicy, 0, null);
    }

    public AnchorsLlmReference(AnchorEngine engine, String contextId, int budget,
                               CompliancePolicy compliancePolicy,
                               int tokenBudget,
                               TokenCounter tokenCounter) {
        this.engine = engine;
        this.contextId = contextId;
        this.budget = budget;
        this.compliancePolicy = compliancePolicy;
        this.tokenBudget = tokenBudget;
        this.tokenCounter = tokenCounter;
        this.budgetEnforcer = new PromptBudgetEnforcer();
    }

    public String getContent() {
        ensureAnchorsLoaded();
        if (selectedAnchors.isEmpty()) {
            return "";
        }

        var grouped = selectedAnchors.stream()
                .collect(Collectors.groupingBy(Anchor::authority));
        var tiers = new java.util.ArrayList<Map<String, Object>>();
        for (var authority : List.of(Authority.CANON, Authority.RELIABLE,
                Authority.UNRELIABLE, Authority.PROVISIONAL)) {
            var group = grouped.getOrDefault(authority, List.of());
            if (group.isEmpty()) {
                continue;
            }
            var anchorMaps = group.stream()
                                  .map(anchor -> Map.<String, Object>of(
                                          "id", anchor.id(),
                                          "text", anchor.text(),
                                          "rank", anchor.rank()))
                                  .toList();
            var strength = compliancePolicy.getStrengthFor(authority);
            tiers.add(Map.of(
                    "authority", authority.name(),
                    "strength", strength.name(),
                    "anchors", anchorMaps));
        }
        var content = PromptTemplates.render("prompts/anchors-reference.jinja", Map.of("tiers", tiers));
        logger.debug("Injected {} anchors for context {}", selectedAnchors.size(), contextId);
        return content;
    }

    public String getLabel() {
        return "anchors";
    }

    public List<Anchor> getAnchors() {
        ensureAnchorsLoaded();
        return selectedAnchors;
    }

    public BudgetResult getLastBudgetResult() {
        ensureAnchorsLoaded();
        return lastBudgetResult;
    }

    /**
     * Invalidate cache for next call.
     */
    public void refresh() {
        cachedAnchors = null;
        selectedAnchors = null;
        lastBudgetResult = null;
    }

    private void ensureAnchorsLoaded() {
        if (selectedAnchors != null) {
            return;
        }
        if (cachedAnchors == null) {
            cachedAnchors = engine.inject(contextId);
        }

        var limited = cachedAnchors.stream().limit(budget).toList();
        if (tokenBudget > 0 && tokenCounter != null) {
            lastBudgetResult = budgetEnforcer.enforce(limited, tokenBudget, tokenCounter, compliancePolicy);
            selectedAnchors = lastBudgetResult.included();
            return;
        }

        selectedAnchors = limited;
        lastBudgetResult = new BudgetResult(limited, List.of(), 0, false);
    }
}
