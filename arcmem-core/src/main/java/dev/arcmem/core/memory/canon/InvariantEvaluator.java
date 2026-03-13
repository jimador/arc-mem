package dev.arcmem.core.memory.canon;

import dev.arcmem.core.memory.model.MemoryUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates operator-defined invariant rules against proposed unit lifecycle actions.
 * <p>
 * Rules with {@link InvariantStrength#MUST} produce blocking violations;
 * rules with {@link InvariantStrength#SHOULD} produce warnings.
 */
@Service
public class InvariantEvaluator {

    private final InvariantRuleProvider ruleProvider;

    public InvariantEvaluator(InvariantRuleProvider ruleProvider) {
        this.ruleProvider = ruleProvider;
    }

    /**
     * Evaluate invariants applicable to a proposed action.
     *
     * @param contextId  the context where the action is occurring
     * @param action     the proposed lifecycle action
     * @param units      current active units in the context
     * @param targetUnit the unit being acted upon (null for context-wide checks)
     *
     * @return evaluation result with any violations
     */
    public InvariantEvaluation evaluate(
            String contextId, ProposedAction action,
            List<MemoryUnit> units, @Nullable MemoryUnit targetUnit) {

        var rules = ruleProvider.rulesForContext(contextId);
        var violations = new ArrayList<InvariantViolationData>();

        for (var rule : rules) {
            if (rule.contextId() != null && !rule.contextId().equals(contextId)) {
                continue;
            }

            var violation = evaluateRule(rule, action, units, targetUnit);
            if (violation != null) {
                violations.add(violation);
            }
        }

        return new InvariantEvaluation(violations, rules.size());
    }

    private @Nullable InvariantViolationData evaluateRule(
            InvariantRule rule, ProposedAction action,
            List<MemoryUnit> units, @Nullable MemoryUnit targetUnit) {

        return switch (rule) {
            case InvariantRule.AuthorityFloor floor -> evaluateAuthorityFloor(floor, action, targetUnit);
            case InvariantRule.EvictionImmunity immunity -> evaluateEvictionImmunity(immunity, action, targetUnit);
            case InvariantRule.MinAuthorityCount minCount -> evaluateMinAuthorityCount(minCount, action, units, targetUnit);
            case InvariantRule.ArchiveProhibition prohibition -> evaluateArchiveProhibition(prohibition, action, targetUnit);
        };
    }

    private @Nullable InvariantViolationData evaluateAuthorityFloor(
            InvariantRule.AuthorityFloor rule, ProposedAction action,
            @Nullable MemoryUnit targetUnit) {
        if (action != ProposedAction.DEMOTE && action != ProposedAction.AUTHORITY_CHANGE) {
            return null;
        }
        if (targetUnit == null) {
            return null;
        }
        if (!matchesPattern(targetUnit.text(), rule.unitTextPattern())) {
            return null;
        }

        if (targetUnit.authority().isAtLeast(rule.minimumAuthority())) {
            return new InvariantViolationData(
                    rule.id(), rule.strength(), action,
                    "Demotion would violate authority floor of " + rule.minimumAuthority()
                    + " for unit matching '" + rule.unitTextPattern() + "'",
                    targetUnit.id());
        }
        return null;
    }

    private @Nullable InvariantViolationData evaluateEvictionImmunity(
            InvariantRule.EvictionImmunity rule, ProposedAction action,
            @Nullable MemoryUnit targetUnit) {
        if (action != ProposedAction.EVICT) {
            return null;
        }
        if (targetUnit == null) {
            return null;
        }
        if (!matchesPattern(targetUnit.text(), rule.unitTextPattern())) {
            return null;
        }

        return new InvariantViolationData(
                rule.id(), rule.strength(), action,
                "Eviction blocked for unit matching '" + rule.unitTextPattern() + "'",
                targetUnit.id());
    }

    private @Nullable InvariantViolationData evaluateMinAuthorityCount(
            InvariantRule.MinAuthorityCount rule, ProposedAction action,
            List<MemoryUnit> units, @Nullable MemoryUnit targetUnit) {
        if (action != ProposedAction.ARCHIVE && action != ProposedAction.EVICT
            && action != ProposedAction.DEMOTE) {
            return null;
        }

        long currentCount = units.stream()
                                 .filter(a -> a.authority().isAtLeast(rule.minimumAuthority()))
                                 .count();

        if (targetUnit != null && targetUnit.authority().isAtLeast(rule.minimumAuthority())) {
            if (currentCount - 1 < rule.minimumCount()) {
                return new InvariantViolationData(
                        rule.id(), rule.strength(), action,
                        "Action would reduce " + rule.minimumAuthority()
                        + " unit count below minimum of " + rule.minimumCount(),
                        targetUnit.id());
            }
        }
        return null;
    }

    private @Nullable InvariantViolationData evaluateArchiveProhibition(
            InvariantRule.ArchiveProhibition rule, ProposedAction action,
            @Nullable MemoryUnit targetUnit) {
        if (action != ProposedAction.ARCHIVE) {
            return null;
        }
        if (targetUnit == null) {
            return null;
        }
        if (!matchesPattern(targetUnit.text(), rule.unitTextPattern())) {
            return null;
        }

        return new InvariantViolationData(
                rule.id(), rule.strength(), action,
                "Archive blocked for unit matching '" + rule.unitTextPattern() + "'",
                targetUnit.id());
    }

    private boolean matchesPattern(String text, String pattern) {
        return text.toLowerCase().contains(pattern.toLowerCase());
    }
}
