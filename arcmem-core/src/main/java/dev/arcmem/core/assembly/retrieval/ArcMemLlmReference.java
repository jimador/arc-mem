package dev.arcmem.core.assembly.retrieval;

import dev.arcmem.core.assembly.budget.BudgetResult;
import dev.arcmem.core.assembly.budget.PromptBudgetEnforcer;
import dev.arcmem.core.assembly.budget.TokenCounter;
import dev.arcmem.core.assembly.compaction.MemoryUnitCacheInvalidator;
import dev.arcmem.core.config.ArcMemProperties.RetrievalConfig;
import dev.arcmem.core.memory.canon.CompliancePolicy;
import dev.arcmem.core.memory.engine.ArcMemEngine;
import dev.arcmem.core.memory.model.Authority;
import dev.arcmem.core.memory.model.MemoryUnit;
import dev.arcmem.core.prompt.PromptPathConstants;
import dev.arcmem.core.prompt.PromptTemplates;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides ranked unit context for injection into the system prompt.
 * Queries ArcMemEngine for active units within budget, formats them
 * as a structured context block with authority-tiered compliance.
 * <p>
 * Supports multiple retrieval modes via {@link RetrievalConfig}:
 * <ul>
 *   <li>{@link RetrievalMode#BULK} — all active units within budget (legacy behavior)</li>
 *   <li>{@link RetrievalMode#HYBRID} — heuristic/LLM-scored selection of top-k units,
 *       with CANON units always included</li>
 *   <li>{@link RetrievalMode#TOOL} — empty baseline; units retrieved on-demand via tool calls</li>
 * </ul>
 * <p>
 * Cache invalidation is event-driven via {@link MemoryUnitCacheInvalidator}: whenever
 * a lifecycle event is published for this context, the next call to
 * {@link #getContent()}, {@link #getUnits()}, or {@link #getLastBudgetResult()}
 * will reload unit data from the engine.
 */
public class ArcMemLlmReference {

    private static final Logger logger = LoggerFactory.getLogger(ArcMemLlmReference.class);

    private final ArcMemEngine engine;
    private final String contextId;
    private final int budget;
    private final CompliancePolicy compliancePolicy;
    private final int tokenBudget;
    private final TokenCounter tokenCounter;
    private final PromptBudgetEnforcer budgetEnforcer;
    private final MemoryUnitCacheInvalidator cacheInvalidator;
    private final @Nullable RetrievalConfig retrievalConfig;
    private final @Nullable RelevanceScorer relevanceScorer;
    private final boolean adaptiveFootprintEnabled;

    private List<MemoryUnit> cachedUnits;
    private List<MemoryUnit> selectedUnits;
    private BudgetResult lastBudgetResult;

    public ArcMemLlmReference(ArcMemEngine engine, String contextId, int budget,
                              CompliancePolicy compliancePolicy) {
        this(engine, contextId, budget, compliancePolicy, 0, null, null, null, null, false);
    }

    public ArcMemLlmReference(ArcMemEngine engine, String contextId, int budget,
                              CompliancePolicy compliancePolicy,
                              int tokenBudget,
                              TokenCounter tokenCounter) {
        this(engine, contextId, budget, compliancePolicy, tokenBudget, tokenCounter, null, null, null, false);
    }

    public ArcMemLlmReference(ArcMemEngine engine, String contextId, int budget,
                              CompliancePolicy compliancePolicy,
                              int tokenBudget,
                              TokenCounter tokenCounter,
                              MemoryUnitCacheInvalidator cacheInvalidator) {
        this(engine, contextId, budget, compliancePolicy, tokenBudget, tokenCounter,
             cacheInvalidator, null, null, false);
    }

    public ArcMemLlmReference(ArcMemEngine engine, String contextId, int budget,
                              CompliancePolicy compliancePolicy,
                              int tokenBudget,
                              TokenCounter tokenCounter,
                              @Nullable MemoryUnitCacheInvalidator cacheInvalidator,
                              @Nullable RetrievalConfig retrievalConfig,
                              @Nullable RelevanceScorer relevanceScorer) {
        this(engine, contextId, budget, compliancePolicy, tokenBudget, tokenCounter,
             cacheInvalidator, retrievalConfig, relevanceScorer, false);
    }

    public ArcMemLlmReference(ArcMemEngine engine, String contextId, int budget,
                              CompliancePolicy compliancePolicy,
                              int tokenBudget,
                              TokenCounter tokenCounter,
                              @Nullable MemoryUnitCacheInvalidator cacheInvalidator,
                              @Nullable RetrievalConfig retrievalConfig,
                              @Nullable RelevanceScorer relevanceScorer,
                              boolean adaptiveFootprintEnabled) {
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
        this.adaptiveFootprintEnabled = adaptiveFootprintEnabled;
    }

    public String getContent() {
        ensureUnitsLoaded();
        if (selectedUnits.isEmpty()) {
            return "";
        }

        if (adaptiveFootprintEnabled) {
            return getContentWithAdaptiveFootprint();
        }

        var grouped = selectedUnits.stream()
                                   .collect(Collectors.groupingBy(MemoryUnit::authority));
        var tiers = new java.util.ArrayList<Map<String, Object>>();
        for (var authority : List.of(Authority.CANON, Authority.RELIABLE,
                                     Authority.UNRELIABLE, Authority.PROVISIONAL)) {
            var group = grouped.getOrDefault(authority, List.of());
            if (group.isEmpty()) {
                continue;
            }
            var unitMaps = group.stream()
                                .map(unit -> {
                                    var map = new HashMap<>(Map.<String, Object>of(
                                            "id", unit.id(),
                                            "text", unit.text(),
                                            "rank", unit.rank()));
                                    if (unit.sourceId() != null) {
                                        map.put("source", unit.sourceId());
                                    }
                                    return map;
                                })
                                .toList();
            var strength = compliancePolicy.getStrengthFor(authority);
            tiers.add(Map.of(
                    "authority", authority.name(),
                    "strength", strength.name(),
                    "units", unitMaps));
        }
        var content = PromptTemplates.render(PromptPathConstants.UNITS_REFERENCE, Map.of("tiers", tiers));
        logger.debug("Injected {} units for context {}", selectedUnits.size(), contextId);
        return content;
    }

    private String getContentWithAdaptiveFootprint() {
        var grouped = selectedUnits.stream()
                                   .collect(Collectors.groupingBy(MemoryUnit::authority));
        var result = new StringBuilder();

        for (var authority : List.of(Authority.CANON, Authority.RELIABLE,
                                     Authority.UNRELIABLE, Authority.PROVISIONAL)) {
            var group = grouped.getOrDefault(authority, List.of());
            if (group.isEmpty()) {
                continue;
            }

            var strength = compliancePolicy.getStrengthFor(authority);
            var complianceLanguage = switch (strength.name()) {
                case "STRICT" -> "MUST be preserved - absolute requirement";
                case "MODERATE" -> "SHOULD be considered - treat with caution";
                default -> "MAY be reconsidered - low confidence";
            };

            result.append("=== ").append(authority.name()).append(" FACTS (")
                  .append(complianceLanguage).append(") ===\n");

            var index = 1;
            for (var unit : group) {
                var template = templateForAuthority(authority);
                var rendered = PromptTemplates.render(template, Map.of(
                        "index", index,
                        "text", unit.text(),
                        "rank", unit.rank()));
                result.append(rendered);
                index++;
            }
            result.append("\n");
        }

        logger.debug("Injected {} units for context {} (adaptive footprint enabled)", selectedUnits.size(), contextId);
        return result.toString();
    }

    private String templateForAuthority(Authority authority) {
        return switch (authority) {
            case CANON -> PromptPathConstants.UNIT_TEMPLATE_CANON;
            case RELIABLE -> PromptPathConstants.UNIT_TEMPLATE_RELIABLE;
            case UNRELIABLE -> PromptPathConstants.UNIT_TEMPLATE_UNRELIABLE;
            case PROVISIONAL -> PromptPathConstants.UNIT_TEMPLATE_PROVISIONAL;
        };
    }

    public String getLabel() {
        return "units";
    }

    public List<MemoryUnit> getUnits() {
        ensureUnitsLoaded();
        return selectedUnits;
    }

    public BudgetResult getLastBudgetResult() {
        ensureUnitsLoaded();
        return lastBudgetResult;
    }

    /**
     * Clears cached state so the next access reloads from the engine.
     * If an {@link MemoryUnitCacheInvalidator} is present, marks the context clean.
     */
    public void refresh() {
        if (cacheInvalidator != null) {
            cacheInvalidator.markClean(contextId);
        }
        cachedUnits = null;
        selectedUnits = null;
        lastBudgetResult = null;
    }

    private void ensureUnitsLoaded() {
        if (selectedUnits != null) {
            if (cacheInvalidator != null && cacheInvalidator.isDirty(contextId)) {
                cacheInvalidator.markClean(contextId);
                cachedUnits = null;
                selectedUnits = null;
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
        if (cachedUnits == null) {
            cachedUnits = engine.inject(contextId);
        }

        var limited = cachedUnits.stream().limit(budget).toList();
        if (tokenBudget > 0 && tokenCounter != null) {
            lastBudgetResult = budgetEnforcer.enforce(limited, tokenBudget, tokenCounter, compliancePolicy, adaptiveFootprintEnabled);
            selectedUnits = lastBudgetResult.included();
        } else {
            selectedUnits = limited;
            lastBudgetResult = new BudgetResult(limited, List.of(), 0, false);
        }

        var span = Span.current();
        span.setAttribute("retrieval.mode", "BULK");
        span.setAttribute("retrieval.baseline_count", selectedUnits.size());
        span.setAttribute("retrieval.filtered_count", 0);
        span.setAttribute("retrieval.avg_relevance_score", 0.0);
    }

    private void loadToolMode() {
        selectedUnits = List.of();
        lastBudgetResult = new BudgetResult(List.of(), List.of(), 0, false);

        var span = Span.current();
        span.setAttribute("retrieval.mode", "TOOL");
        span.setAttribute("retrieval.baseline_count", 0);
        span.setAttribute("retrieval.filtered_count", 0);
        span.setAttribute("retrieval.avg_relevance_score", 0.0);
    }

    private void loadHybridMode() {
        if (cachedUnits == null) {
            cachedUnits = engine.inject(contextId);
        }

        var allUnits = cachedUnits.stream().limit(budget).toList();

        if (relevanceScorer == null) {
            logger.warn("RelevanceScorer unavailable in HYBRID mode — falling back to BULK behavior");
            loadBulkMode();
            return;
        }

        var scoring = retrievalConfig != null ? retrievalConfig.scoring() : null;
        var scored = relevanceScorer.scoreAndRank(allUnits, scoring);

        var canonUnits = allUnits.stream()
                                 .filter(a -> a.authority() == Authority.CANON)
                                 .toList();
        var canonIds = canonUnits.stream().map(MemoryUnit::id).collect(Collectors.toSet());

        var minRelevance = retrievalConfig != null ? retrievalConfig.minRelevance() : 0.0;
        var topK = retrievalConfig != null ? retrievalConfig.baselineTopK() : 5;

        var topNonCanon = scored.stream()
                                .filter(sa -> !canonIds.contains(sa.id()))
                                .filter(sa -> sa.relevanceScore() >= minRelevance)
                                .limit(topK)
                                .toList();

        var selectedIds = topNonCanon.stream().map(ScoredMemoryUnit::id).collect(Collectors.toSet());
        var topUnits = allUnits.stream()
                               .filter(a -> selectedIds.contains(a.id()))
                               .toList();

        var baseline = new ArrayList<>(canonUnits);
        baseline.addAll(topUnits);

        var filteredCount = (int) scored.stream()
                                        .filter(sa -> !canonIds.contains(sa.id()))
                                        .filter(sa -> sa.relevanceScore() < minRelevance)
                                        .count();
        var avgScore = scored.isEmpty() ? 0.0
                : scored.stream().mapToDouble(ScoredMemoryUnit::relevanceScore).average().orElse(0.0);

        logger.debug("HYBRID retrieval: {} total units, {} CANON always-included, {} non-CANON selected (top-k={}, minRelevance={})",
                     allUnits.size(), canonUnits.size(), topUnits.size(), topK, minRelevance);

        if (tokenBudget > 0 && tokenCounter != null) {
            lastBudgetResult = budgetEnforcer.enforce(baseline, tokenBudget, tokenCounter, compliancePolicy, adaptiveFootprintEnabled);
            selectedUnits = lastBudgetResult.included();
        } else {
            selectedUnits = List.copyOf(baseline);
            lastBudgetResult = new BudgetResult(selectedUnits, List.of(), 0, false);
        }

        var span = Span.current();
        span.setAttribute("retrieval.mode", "HYBRID");
        span.setAttribute("retrieval.baseline_count", selectedUnits.size());
        span.setAttribute("retrieval.filtered_count", filteredCount);
        span.setAttribute("retrieval.avg_relevance_score", avgScore);
    }
}
