package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.DiceAnchorsProperties.RetrievalConfig;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.CompliancePolicy;
import dev.dunnam.diceanchors.prompt.PromptPathConstants;
import dev.dunnam.diceanchors.prompt.PromptTemplates;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides ranked anchor context for injection into the system prompt.
 * Queries AnchorEngine for active anchors within budget, formats them
 * as a structured context block with authority-tiered compliance.
 * <p>
 * Supports multiple retrieval modes via {@link RetrievalConfig}:
 * <ul>
 *   <li>{@link RetrievalMode#BULK} — all active anchors within budget (legacy behavior)</li>
 *   <li>{@link RetrievalMode#HYBRID} — heuristic/LLM-scored selection of top-k anchors,
 *       with CANON anchors always included</li>
 *   <li>{@link RetrievalMode#TOOL} — empty baseline; anchors retrieved on-demand via tool calls</li>
 * </ul>
 * <p>
 * Cache invalidation is event-driven via {@link AnchorCacheInvalidator}: whenever
 * a lifecycle event is published for this context, the next call to
 * {@link #getContent()}, {@link #getAnchors()}, or {@link #getLastBudgetResult()}
 * will reload anchor data from the engine.
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
    private final AnchorCacheInvalidator cacheInvalidator;
    private final @Nullable RetrievalConfig retrievalConfig;
    private final @Nullable RelevanceScorer relevanceScorer;

    private List<Anchor> cachedAnchors;
    private List<Anchor> selectedAnchors;
    private BudgetResult lastBudgetResult;

    public AnchorsLlmReference(AnchorEngine engine, String contextId, int budget,
                               CompliancePolicy compliancePolicy) {
        this(engine, contextId, budget, compliancePolicy, 0, null, null, null, null);
    }

    public AnchorsLlmReference(AnchorEngine engine, String contextId, int budget,
                               CompliancePolicy compliancePolicy,
                               int tokenBudget,
                               TokenCounter tokenCounter) {
        this(engine, contextId, budget, compliancePolicy, tokenBudget, tokenCounter, null, null, null);
    }

    public AnchorsLlmReference(AnchorEngine engine, String contextId, int budget,
                               CompliancePolicy compliancePolicy,
                               int tokenBudget,
                               TokenCounter tokenCounter,
                               AnchorCacheInvalidator cacheInvalidator) {
        this(engine, contextId, budget, compliancePolicy, tokenBudget, tokenCounter,
                cacheInvalidator, null, null);
    }

    public AnchorsLlmReference(AnchorEngine engine, String contextId, int budget,
                               CompliancePolicy compliancePolicy,
                               int tokenBudget,
                               TokenCounter tokenCounter,
                               @Nullable AnchorCacheInvalidator cacheInvalidator,
                               @Nullable RetrievalConfig retrievalConfig,
                               @Nullable RelevanceScorer relevanceScorer) {
        this.engine = engine;
        this.contextId = contextId;
        this.budget = budget;
        this.compliancePolicy = compliancePolicy;
        this.tokenBudget = tokenBudget;
        this.tokenCounter = tokenCounter;
        this.budgetEnforcer = new PromptBudgetEnforcer();
        this.cacheInvalidator = cacheInvalidator;
        this.retrievalConfig = retrievalConfig;
        this.relevanceScorer = relevanceScorer;
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
        var content = PromptTemplates.render(PromptPathConstants.ANCHORS_REFERENCE, Map.of("tiers", tiers));
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
     * Clears cached state so the next access reloads from the engine.
     * If an {@link AnchorCacheInvalidator} is present, marks the context clean.
     */
    public void refresh() {
        if (cacheInvalidator != null) {
            cacheInvalidator.markClean(contextId);
        }
        cachedAnchors = null;
        selectedAnchors = null;
        lastBudgetResult = null;
    }

    private void ensureAnchorsLoaded() {
        if (selectedAnchors != null) {
            if (cacheInvalidator != null && cacheInvalidator.isDirty(contextId)) {
                cacheInvalidator.markClean(contextId);
                cachedAnchors = null;
                selectedAnchors = null;
                lastBudgetResult = null;
            } else {
                return;
            }
        }

        var mode = resolveMode();
        switch (mode) {
            case TOOL -> loadToolMode();
            case HYBRID -> loadHybridMode();
            default -> loadBulkMode();
        }
    }

    private RetrievalMode resolveMode() {
        if (retrievalConfig == null) {
            return RetrievalMode.BULK;
        }
        return retrievalConfig.mode();
    }

    private void loadBulkMode() {
        if (cachedAnchors == null) {
            cachedAnchors = engine.inject(contextId);
        }

        var limited = cachedAnchors.stream().limit(budget).toList();
        if (tokenBudget > 0 && tokenCounter != null) {
            lastBudgetResult = budgetEnforcer.enforce(limited, tokenBudget, tokenCounter, compliancePolicy);
            selectedAnchors = lastBudgetResult.included();
        } else {
            selectedAnchors = limited;
            lastBudgetResult = new BudgetResult(limited, List.of(), 0, false);
        }

        var span = Span.current();
        span.setAttribute("retrieval.mode", "BULK");
        span.setAttribute("retrieval.baseline_count", selectedAnchors.size());
        span.setAttribute("retrieval.filtered_count", 0);
        span.setAttribute("retrieval.avg_relevance_score", 0.0);
    }

    private void loadToolMode() {
        selectedAnchors = List.of();
        lastBudgetResult = new BudgetResult(List.of(), List.of(), 0, false);

        var span = Span.current();
        span.setAttribute("retrieval.mode", "TOOL");
        span.setAttribute("retrieval.baseline_count", 0);
        span.setAttribute("retrieval.filtered_count", 0);
        span.setAttribute("retrieval.avg_relevance_score", 0.0);
    }

    private void loadHybridMode() {
        if (cachedAnchors == null) {
            cachedAnchors = engine.inject(contextId);
        }

        var allAnchors = cachedAnchors.stream().limit(budget).toList();

        if (relevanceScorer == null) {
            logger.warn("RelevanceScorer unavailable in HYBRID mode — falling back to BULK behavior");
            loadBulkMode();
            return;
        }

        var scoring = retrievalConfig != null ? retrievalConfig.scoring() : null;
        var scored = relevanceScorer.scoreAndRank(allAnchors, scoring);

        var canonAnchors = allAnchors.stream()
                .filter(a -> a.authority() == Authority.CANON)
                .toList();
        var canonIds = canonAnchors.stream().map(Anchor::id).collect(Collectors.toSet());

        var minRelevance = retrievalConfig != null ? retrievalConfig.minRelevance() : 0.0;
        var topK = retrievalConfig != null ? retrievalConfig.baselineTopK() : 5;

        var topNonCanon = scored.stream()
                .filter(sa -> !canonIds.contains(sa.id()))
                .filter(sa -> sa.relevanceScore() >= minRelevance)
                .limit(topK)
                .toList();

        var selectedIds = topNonCanon.stream().map(ScoredAnchor::id).collect(Collectors.toSet());
        var topAnchors = allAnchors.stream()
                .filter(a -> selectedIds.contains(a.id()))
                .toList();

        var baseline = new ArrayList<>(canonAnchors);
        baseline.addAll(topAnchors);

        var filteredCount = (int) scored.stream()
                .filter(sa -> !canonIds.contains(sa.id()))
                .filter(sa -> sa.relevanceScore() < minRelevance)
                .count();
        var avgScore = scored.isEmpty() ? 0.0
                : scored.stream().mapToDouble(ScoredAnchor::relevanceScore).average().orElse(0.0);

        logger.debug("HYBRID retrieval: {} total anchors, {} CANON always-included, {} non-CANON selected (top-k={}, minRelevance={})",
                allAnchors.size(), canonAnchors.size(), topAnchors.size(), topK, minRelevance);

        if (tokenBudget > 0 && tokenCounter != null) {
            lastBudgetResult = budgetEnforcer.enforce(baseline, tokenBudget, tokenCounter, compliancePolicy);
            selectedAnchors = lastBudgetResult.included();
        } else {
            selectedAnchors = List.copyOf(baseline);
            lastBudgetResult = new BudgetResult(selectedAnchors, List.of(), 0, false);
        }

        var span = Span.current();
        span.setAttribute("retrieval.mode", "HYBRID");
        span.setAttribute("retrieval.baseline_count", selectedAnchors.size());
        span.setAttribute("retrieval.filtered_count", filteredCount);
        span.setAttribute("retrieval.avg_relevance_score", avgScore);
    }
}
