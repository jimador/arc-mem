package dev.arcmem.core.memory.trust;
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
import dev.arcmem.core.persistence.PropositionNode;

import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Trust signal that scores how relevant a proposition is to the current conversation context.
 * <p>
 * Importance = |proposition tokens ∩ context tokens| / |proposition tokens|, where context
 * tokens are the union of all active unit texts. Returns 0.5 (neutral) when no units
 * exist. Returns {@link OptionalDouble#empty()} when quality scoring is disabled.
 */
public record ImportanceSignal(ArcMemEngine arcMemEngine, ArcMemProperties properties) implements TrustSignal {

    @Override
    public String name() {
        return "importance";
    }

    @Override
    public OptionalDouble evaluate(PropositionNode proposition, String contextId) {
        try {
            var qualityConfig = properties.unit().qualityScoring();
            if (qualityConfig == null || !qualityConfig.enabled()) {
                return OptionalDouble.empty();
            }

            var units = arcMemEngine.inject(contextId);
            if (units.isEmpty()) {
                return OptionalDouble.of(0.5);
            }

            var propositionTokens = NoveltySignal.tokenize(proposition.getText());
            if (propositionTokens.isEmpty()) {
                return OptionalDouble.of(0.0);
            }

            Set<String> contextTokens = units.stream()
                    .flatMap(unit -> NoveltySignal.tokenize(unit.text()).stream())
                    .collect(Collectors.toSet());

            long overlap = propositionTokens.stream().filter(contextTokens::contains).count();
            double importance = (double) overlap / propositionTokens.size();

            return OptionalDouble.of(Math.max(0.0, Math.min(1.0, importance)));
        } catch (Exception e) {
            return OptionalDouble.empty();
        }
    }
}
