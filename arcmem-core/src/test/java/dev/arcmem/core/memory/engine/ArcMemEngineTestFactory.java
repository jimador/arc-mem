package dev.arcmem.core.memory.engine;

import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.core.memory.budget.BudgetStrategy;
import dev.arcmem.core.memory.canon.CanonizationGate;
import dev.arcmem.core.memory.canon.InvariantEvaluator;
import dev.arcmem.core.memory.conflict.ConflictDetector;
import dev.arcmem.core.memory.conflict.ConflictResolver;
import dev.arcmem.core.memory.maintenance.DecayPolicy;
import dev.arcmem.core.memory.mutation.ReinforcementPolicy;
import dev.arcmem.core.memory.trust.TrustPipeline;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Constructs an {@link ArcMemEngine} with its internal services for unit tests
 * that previously used the monolithic constructor.
 */
final class ArcMemEngineTestFactory {

    private ArcMemEngineTestFactory() {}

    static ArcMemEngine create(
            MemoryUnitRepository repository,
            ArcMemProperties properties,
            ConflictDetector conflictDetector,
            ConflictResolver conflictResolver,
            ReinforcementPolicy reinforcementPolicy,
            DecayPolicy decayPolicy,
            ApplicationEventPublisher eventPublisher,
            TrustPipeline trustPipeline,
            CanonizationGate canonizationGate,
            InvariantEvaluator invariantEvaluator,
            BudgetStrategy budgetStrategy) {

        var lifecycleService = new MemoryUnitLifecycleService(
                repository, properties, reinforcementPolicy, decayPolicy,
                eventPublisher, trustPipeline, canonizationGate,
                invariantEvaluator, budgetStrategy);

        var queryService = new MemoryUnitQueryService(repository, properties);

        return new ArcMemEngine(
                lifecycleService, queryService,
                conflictDetector, conflictResolver,
                repository, invariantEvaluator,
                eventPublisher, properties);
    }
}
