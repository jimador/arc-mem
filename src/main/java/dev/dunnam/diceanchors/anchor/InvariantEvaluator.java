package dev.dunnam.diceanchors.anchor;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates operator-defined invariant rules against proposed anchor lifecycle actions.
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
     * @param contextId    the context where the action is occurring
     * @param action       the proposed lifecycle action
     * @param anchors      current active anchors in the context
     * @param targetAnchor the anchor being acted upon (null for context-wide checks)
     * @return evaluation result with any violations
     */
    public InvariantEvaluation evaluate(
            String contextId, ProposedAction action,
            List<Anchor> anchors, @Nullable Anchor targetAnchor) {

        var rules = ruleProvider.rulesForContext(contextId);
        var violations = new ArrayList<InvariantViolationData>();

        for (var rule : rules) {
            // Global rules (contextId==null) apply everywhere;
            // context-specific rules only apply to their context
            if (rule.contextId() != null && !rule.contextId().equals(contextId)) {
                continue;
            }

            var violation = evaluateRule(rule, action, anchors, targetAnchor);
            if (violation != null) {
                violations.add(violation);
            }
        }

        return new InvariantEvaluation(violations, rules.size());
    }

    private @Nullable InvariantViolationData evaluateRule(
            InvariantRule rule, ProposedAction action,
            List<Anchor> anchors, @Nullable Anchor targetAnchor) {

        return switch (rule) {
            case InvariantRule.AuthorityFloor floor ->
                    evaluateAuthorityFloor(floor, action, targetAnchor);
            case InvariantRule.EvictionImmunity immunity ->
                    evaluateEvictionImmunity(immunity, action, targetAnchor);
            case InvariantRule.MinAuthorityCount minCount ->
                    evaluateMinAuthorityCount(minCount, action, anchors, targetAnchor);
            case InvariantRule.ArchiveProhibition prohibition ->
                    evaluateArchiveProhibition(prohibition, action, targetAnchor);
        };
    }

    private @Nullable InvariantViolationData evaluateAuthorityFloor(
            InvariantRule.AuthorityFloor rule, ProposedAction action,
            @Nullable Anchor targetAnchor) {
        if (action != ProposedAction.DEMOTE && action != ProposedAction.AUTHORITY_CHANGE) {
            return null;
        }
        if (targetAnchor == null) return null;
        if (!matchesPattern(targetAnchor.text(), rule.anchorTextPattern())) return null;

        if (targetAnchor.authority().isAtLeast(rule.minimumAuthority())) {
            return new InvariantViolationData(
                    rule.id(), rule.strength(), action,
                    "Demotion would violate authority floor of " + rule.minimumAuthority()
                            + " for anchor matching '" + rule.anchorTextPattern() + "'",
                    targetAnchor.id());
        }
        return null;
    }

    private @Nullable InvariantViolationData evaluateEvictionImmunity(
            InvariantRule.EvictionImmunity rule, ProposedAction action,
            @Nullable Anchor targetAnchor) {
        if (action != ProposedAction.EVICT) return null;
        if (targetAnchor == null) return null;
        if (!matchesPattern(targetAnchor.text(), rule.anchorTextPattern())) return null;

        return new InvariantViolationData(
                rule.id(), rule.strength(), action,
                "Eviction blocked for anchor matching '" + rule.anchorTextPattern() + "'",
                targetAnchor.id());
    }

    private @Nullable InvariantViolationData evaluateMinAuthorityCount(
            InvariantRule.MinAuthorityCount rule, ProposedAction action,
            List<Anchor> anchors, @Nullable Anchor targetAnchor) {
        if (action != ProposedAction.ARCHIVE && action != ProposedAction.EVICT
                && action != ProposedAction.DEMOTE) {
            return null;
        }

        long currentCount = anchors.stream()
                .filter(a -> a.authority().isAtLeast(rule.minimumAuthority()))
                .count();

        if (targetAnchor != null && targetAnchor.authority().isAtLeast(rule.minimumAuthority())) {
            if (currentCount - 1 < rule.minimumCount()) {
                return new InvariantViolationData(
                        rule.id(), rule.strength(), action,
                        "Action would reduce " + rule.minimumAuthority()
                                + " anchor count below minimum of " + rule.minimumCount(),
                        targetAnchor.id());
            }
        }
        return null;
    }

    private @Nullable InvariantViolationData evaluateArchiveProhibition(
            InvariantRule.ArchiveProhibition rule, ProposedAction action,
            @Nullable Anchor targetAnchor) {
        if (action != ProposedAction.ARCHIVE) return null;
        if (targetAnchor == null) return null;
        if (!matchesPattern(targetAnchor.text(), rule.anchorTextPattern())) return null;

        return new InvariantViolationData(
                rule.id(), rule.strength(), action,
                "Archive blocked for anchor matching '" + rule.anchorTextPattern() + "'",
                targetAnchor.id());
    }

    private boolean matchesPattern(String text, String pattern) {
        return text.toLowerCase().contains(pattern.toLowerCase());
    }
}
