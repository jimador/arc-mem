package dev.arcmem.core.assembly.budget;
import dev.arcmem.core.memory.budget.*;
import dev.arcmem.core.memory.canon.*;
import dev.arcmem.core.memory.conflict.*;
import dev.arcmem.core.memory.engine.*;
import dev.arcmem.core.memory.maintenance.*;
import dev.arcmem.core.memory.model.*;
import dev.arcmem.core.memory.mutation.*;
import dev.arcmem.core.memory.trust.*;
import dev.arcmem.core.assembly.budget.*;
import dev.arcmem.core.assembly.compaction.*;
import dev.arcmem.core.assembly.compliance.*;
import dev.arcmem.core.assembly.protection.*;
import dev.arcmem.core.assembly.retrieval.*;

import dev.arcmem.core.prompt.PromptPathConstants;
import dev.arcmem.core.prompt.PromptTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Applies token-budget constraints to unit prompt assembly while preserving
 * mandatory preamble/protocol text and all CANON units.
 * <p>
 * <strong>Budget algorithm:</strong> Given a list of candidate units and a token budget,
 * this enforcer iteratively drops units in drop-order until the estimated token count
 * falls within budget or no more droppable units remain.
 * <p>
 * <strong>Drop order</strong> (units dropped earliest are listed first):
 * <ol>
 *   <li>PROVISIONAL — lowest trust, highest eviction priority</li>
 *   <li>UNRELIABLE</li>
 *   <li>RELIABLE — dropped last among non-CANON units</li>
 *   <li>CANON — <em>never dropped</em>, always included regardless of budget</li>
 * </ol>
 * Within each authority tier, units are sorted by:
 * <ol>
 *   <li>{@code diceImportance} ascending — units with low DICE importance (0.0 default)
 *       are dropped before high-importance units</li>
 *   <li>{@code rank} ascending — within the same importance value, lower-ranked units
 *       are dropped first, preserving the original rank-based behavior</li>
 * </ol>
 * <p>
 * <strong>Mandatory overhead:</strong> The enforcer accounts for a fixed token cost
 * from the unit reference preamble template
 * ({@link dev.arcmem.core.prompt.PromptPathConstants#UNITS_REFERENCE_OVERHEAD}).
 * This overhead is always counted against the budget, even when the unit list is empty.
 */
public class PromptBudgetEnforcer {

    private static final Logger logger = LoggerFactory.getLogger(PromptBudgetEnforcer.class);

    private static final String MANDATORY_OVERHEAD =
            PromptTemplates.load(PromptPathConstants.UNITS_REFERENCE_OVERHEAD);

    /**
     * Enforce the token budget on a list of candidate units.
     * <p>
     * CANON units are always included. Non-CANON units are dropped in
     * priority order (PROVISIONAL first, then UNRELIABLE, then RELIABLE), with
     * low-importance units dropped before high-importance ones within each tier.
     * If the budget cannot be satisfied even after dropping all non-CANON units,
     * the result is returned with {@code budgetExceeded = true} and the full CANON
     * set still included.
     *
     * @param units     candidate units to fit within budget; MUST NOT be null
     * @param tokenBudget maximum allowed tokens; pass {@code <= 0} to skip enforcement
     * @param counter     token counter for estimation
     * @param policy      compliance policy (currently unused in budget math, reserved)
     * @return a {@link BudgetResult} where {@link BudgetResult#included()} are the units
     *         that fit within budget and {@link BudgetResult#excluded()} are those dropped
     */
    public BudgetResult enforce(List<MemoryUnit> units,
                                int tokenBudget,
                                TokenCounter counter,
                                CompliancePolicy policy) {
        return enforce(units, tokenBudget, counter, policy, false);
    }

    /**
     * Enforce the token budget on a list of candidate units with adaptive footprint option.
     * <p>
     * When {@code adaptiveFootprintEnabled} is true, per-unit token estimation uses
     * authority-specific templates, resulting in different costs per authority level
     * (CANON units cost fewer tokens, PROVISIONAL units cost more).
     * <p>
     * When {@code adaptiveFootprintEnabled} is false, all units are estimated uniformly.
     *
     * @param units                  candidate units to fit within budget; MUST NOT be null
     * @param tokenBudget              maximum allowed tokens; pass {@code <= 0} to skip enforcement
     * @param counter                  token counter for estimation
     * @param policy                   compliance policy (currently unused in budget math, reserved)
     * @param adaptiveFootprintEnabled whether to use authority-specific template estimation
     * @return a {@link BudgetResult} where {@link BudgetResult#included()} are the units
     *         that fit within budget and {@link BudgetResult#excluded()} are those dropped
     */
    public BudgetResult enforce(List<MemoryUnit> units,
                                int tokenBudget,
                                TokenCounter counter,
                                CompliancePolicy policy,
                                boolean adaptiveFootprintEnabled) {
        if (units == null || units.isEmpty()) {
            var baseTokens = counter.estimate(MANDATORY_OVERHEAD);
            return new BudgetResult(List.of(), List.of(), baseTokens, baseTokens > tokenBudget);
        }
        if (tokenBudget <= 0) {
            return new BudgetResult(List.copyOf(units), List.of(), estimateTotal(counter, units, adaptiveFootprintEnabled), false);
        }

        var included = new ArrayList<>(units);
        var excluded = new ArrayList<MemoryUnit>();

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
                    .filter(unit -> unit.authority() == authority)
                    .sorted(Comparator.comparingInt((MemoryUnit a) -> a.memoryTier().ordinal())
                            .thenComparingDouble(MemoryUnit::diceImportance)
                            .thenComparingInt(MemoryUnit::rank))
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

        var canonOnlyExceeded = included.stream().allMatch(unit -> unit.authority() == Authority.CANON)
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

    private int estimateTotal(TokenCounter counter, List<MemoryUnit> units, boolean adaptiveFootprintEnabled) {
        var tokens = counter.estimate(MANDATORY_OVERHEAD);
        for (var unit : units) {
            tokens += estimateUnitTokens(counter, unit, adaptiveFootprintEnabled);
        }
        return tokens;
    }

    private int estimateUnitTokens(TokenCounter counter, MemoryUnit unit, boolean adaptiveFootprintEnabled) {
        if (!adaptiveFootprintEnabled) {
            return counter.estimate(unit.text()) + counter.estimate(" (rank: 999)");
        }
        var template = templateForAuthority(unit.authority());
        var rendered = PromptTemplates.render(template, Map.of(
                "index", 1,
                "text", unit.text(),
                "rank", unit.rank()));
        return counter.estimate(rendered);
    }

    private String templateForAuthority(Authority authority) {
        return switch (authority) {
            case CANON -> PromptPathConstants.UNIT_TEMPLATE_CANON;
            case RELIABLE -> PromptPathConstants.UNIT_TEMPLATE_RELIABLE;
            case UNRELIABLE -> PromptPathConstants.UNIT_TEMPLATE_UNRELIABLE;
            case PROVISIONAL -> PromptPathConstants.UNIT_TEMPLATE_PROVISIONAL;
        };
    }
}
