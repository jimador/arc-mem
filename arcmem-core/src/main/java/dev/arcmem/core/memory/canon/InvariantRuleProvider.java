package dev.arcmem.core.memory.canon;
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

import dev.arcmem.core.config.ArcMemProperties;
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

    public InvariantRuleProvider(ArcMemProperties properties) {
        var config = properties.unit().invariants();
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

    public static InvariantRule toRule(ArcMemProperties.InvariantRuleDefinition def) {
        return switch (def.type()) {
            case AUTHORITY_FLOOR -> new InvariantRule.AuthorityFloor(
                    def.id(), def.strength(), def.contextId(),
                    def.unitTextPattern(), def.minimumAuthority());
            case EVICTION_IMMUNITY -> new InvariantRule.EvictionImmunity(
                    def.id(), def.strength(), def.contextId(), def.unitTextPattern());
            case MIN_AUTHORITY_COUNT -> new InvariantRule.MinAuthorityCount(
                    def.id(), def.strength(), def.contextId(),
                    def.minimumAuthority(), def.minimumCount());
            case ARCHIVE_PROHIBITION -> new InvariantRule.ArchiveProhibition(
                    def.id(), def.strength(), def.contextId(), def.unitTextPattern());
        };
    }
}
