package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Provides invariant rules for a given context, merging global rules from
 * configuration with context-scoped rules registered at runtime.
 */
@Service
public class InvariantRuleProvider {

    private final List<InvariantRule> globalRules;
    private final ConcurrentMap<String, List<InvariantRule>> contextRules = new ConcurrentHashMap<>();

    public InvariantRuleProvider(DiceAnchorsProperties properties) {
        var config = properties.anchor().invariants();
        if (config == null || config.rules() == null) {
            this.globalRules = List.of();
        } else {
            this.globalRules = config.rules().stream()
                    .map(InvariantRuleProvider::toRule)
                    .toList();
        }
    }

    public List<InvariantRule> rulesForContext(String contextId) {
        var rules = new ArrayList<>(globalRules);
        var ctxRules = contextRules.get(contextId);
        if (ctxRules != null) {
            rules.addAll(ctxRules);
        }
        return List.copyOf(rules);
    }

    public void registerForContext(String contextId, List<InvariantRule> rules) {
        contextRules.put(contextId, List.copyOf(rules));
    }

    public void deregisterForContext(String contextId) {
        contextRules.remove(contextId);
    }

    public static InvariantRule toRule(DiceAnchorsProperties.InvariantRuleDefinition def) {
        var strength = InvariantStrength.valueOf(def.strength());
        return switch (def.type()) {
            case "authority-floor" -> new InvariantRule.AuthorityFloor(
                    def.id(), strength, def.contextId(),
                    def.anchorTextPattern(), Authority.valueOf(def.minimumAuthority()));
            case "eviction-immunity" -> new InvariantRule.EvictionImmunity(
                    def.id(), strength, def.contextId(), def.anchorTextPattern());
            case "min-authority-count" -> new InvariantRule.MinAuthorityCount(
                    def.id(), strength, def.contextId(),
                    Authority.valueOf(def.minimumAuthority()), def.minimumCount());
            case "archive-prohibition" -> new InvariantRule.ArchiveProhibition(
                    def.id(), strength, def.contextId(), def.anchorTextPattern());
            default -> throw new IllegalArgumentException("Unknown invariant rule type: " + def.type());
        };
    }
}
