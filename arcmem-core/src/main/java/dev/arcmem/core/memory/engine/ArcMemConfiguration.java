package dev.arcmem.core.memory.engine;

import dev.arcmem.core.assembly.retrieval.RelevanceScorer;
import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.core.extraction.CompositeDuplicateDetector;
import dev.arcmem.core.extraction.DuplicateDetector;
import dev.arcmem.core.extraction.LlmDuplicateDetector;
import dev.arcmem.core.extraction.NormalizedStringDuplicateDetector;
import dev.arcmem.core.memory.canon.CanonizationGate;
import dev.arcmem.core.memory.canon.InvariantEvaluator;
import dev.arcmem.core.memory.conflict.AuthorityConflictResolver;
import dev.arcmem.core.memory.conflict.CompositeConflictDetector;
import dev.arcmem.core.memory.conflict.ConflictDetectionStrategy;
import dev.arcmem.core.memory.conflict.ConflictDetector;
import dev.arcmem.core.memory.conflict.ConflictIndex;
import dev.arcmem.core.memory.conflict.ConflictResolver;
import dev.arcmem.core.memory.conflict.InMemoryConflictIndex;
import dev.arcmem.core.memory.conflict.LlmConflictDetector;
import dev.arcmem.core.memory.conflict.NegationConflictDetector;
import dev.arcmem.core.memory.conflict.RevisionAwareConflictResolver;
import dev.arcmem.core.memory.conflict.SubjectFilter;
import dev.arcmem.core.memory.maintenance.DecayPolicy;
import dev.arcmem.core.memory.maintenance.HybridMaintenanceStrategy;
import dev.arcmem.core.memory.maintenance.MaintenanceMode;
import dev.arcmem.core.memory.maintenance.MaintenanceStrategy;
import dev.arcmem.core.memory.maintenance.MemoryPressureGauge;
import dev.arcmem.core.memory.maintenance.ProactiveMaintenanceStrategy;
import dev.arcmem.core.memory.maintenance.ReactiveMaintenanceStrategy;
import dev.arcmem.core.memory.mutation.MemoryUnitMutationStrategy;
import dev.arcmem.core.memory.mutation.ReinforcementPolicy;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.spi.llm.LlmCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(ArcMemProperties.class)
public class ArcMemConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ArcMemConfiguration.class);

    private final ArcMemProperties properties;

    public ArcMemConfiguration(ArcMemProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean(ConflictIndex.class)
    InMemoryConflictIndex conflictIndex() {
        return new InMemoryConflictIndex();
    }

    @Bean
    ConflictDetector conflictDetector(ChatModel chatModel, LlmCallService llmCallService,
                                      InMemoryConflictIndex conflictIndex) {
        var conflict = properties.conflict();
        var detection = properties.conflictDetection();
        return switch (detection.strategy()) {
            case LEXICAL -> {
                logger.info("Using lexical-only conflict detection (overlap threshold: {})",
                            conflict.negationOverlapThreshold());
                yield new NegationConflictDetector(conflict.negationOverlapThreshold());
            }
            case HYBRID -> {
                logger.info("Using hybrid conflict detection (lexical + semantic with subject filter)");
                yield new CompositeConflictDetector(
                        new NegationConflictDetector(conflict.negationOverlapThreshold()),
                        new LlmConflictDetector(conflict.llmConfidence(), chatModel, detection.model(),
                                                llmCallService),
                        new SubjectFilter(),
                        ConflictDetectionStrategy.LEXICAL_THEN_SEMANTIC,
                        conflictIndex
                );
            }
            case LLM -> {
                logger.info("Using LLM-based conflict detection (confidence: {})", conflict.llmConfidence());
                yield new LlmConflictDetector(conflict.llmConfidence(), chatModel, detection.model(),
                                              llmCallService);
            }
            case INDEXED -> {
                logger.info("Using indexed conflict detection (index-first with LLM fallback)");
                yield new CompositeConflictDetector(
                        new NegationConflictDetector(conflict.negationOverlapThreshold()),
                        new LlmConflictDetector(conflict.llmConfidence(), chatModel, detection.model(),
                                                llmCallService),
                        new SubjectFilter(),
                        ConflictDetectionStrategy.INDEXED,
                        conflictIndex
                );
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(AuthorityConflictResolver.class)
    AuthorityConflictResolver authorityConflictResolver() {
        var conflict = properties.conflict();
        return new AuthorityConflictResolver(
                conflict.replaceThreshold(),
                conflict.demoteThreshold(),
                conflict.tier()
        );
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(value = ConflictResolver.class, ignored = AuthorityConflictResolver.class)
    @ConditionalOnProperty(
            prefix = "arc-mem.unit.revision",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    ConflictResolver revisionAwareConflictResolver(AuthorityConflictResolver authorityResolver,
                                                   MemoryUnitMutationStrategy mutationStrategy) {
        var revision = properties.unit().revision();
        return new RevisionAwareConflictResolver(
                authorityResolver,
                mutationStrategy,
                revision.enabled(),
                revision.reliableRevisable(),
                revision.confidenceThreshold(),
                properties.conflict().replaceThreshold());
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(value = ConflictResolver.class, ignored = AuthorityConflictResolver.class)
    @ConditionalOnProperty(
            prefix = "arc-mem.unit.revision",
            name = "enabled",
            havingValue = "false")
    ConflictResolver authorityPrimaryConflictResolver(AuthorityConflictResolver authorityResolver) {
        return authorityResolver;
    }

    @Bean
    @ConditionalOnMissingBean
    ReinforcementPolicy reinforcementPolicy() {
        return ReinforcementPolicy.threshold();
    }

    @Bean
    @ConditionalOnMissingBean
    DecayPolicy decayPolicy() {
        return DecayPolicy.exponential(24.0);
    }

    @Bean
    @ConditionalOnMissingBean
    MaintenanceStrategy maintenanceStrategy(
            DecayPolicy decayPolicy,
            ReinforcementPolicy reinforcementPolicy,
            MemoryPressureGauge pressureGauge,
            @Lazy ArcMemEngine arcMemEngine,
            MemoryUnitRepository contextUnitRepository,
            CanonizationGate canonizationGate,
            InvariantEvaluator invariantEvaluator,
            LlmCallService llmCallService) {
        var mode = properties.maintenance() != null
                ? properties.maintenance().mode()
                : MaintenanceMode.REACTIVE;
        return switch (mode) {
            case REACTIVE -> {
                logger.info("Using reactive maintenance strategy (per-turn only)");
                yield new ReactiveMaintenanceStrategy(decayPolicy, reinforcementPolicy);
            }
            case PROACTIVE -> {
                logger.info("Using proactive maintenance strategy (F07 5-step sweep)");
                yield proactiveStrategy(pressureGauge, arcMemEngine, contextUnitRepository,
                                        canonizationGate, invariantEvaluator, llmCallService);
            }
            case HYBRID -> {
                logger.info("Using hybrid maintenance strategy (reactive + proactive)");
                var reactive = new ReactiveMaintenanceStrategy(decayPolicy, reinforcementPolicy);
                yield new HybridMaintenanceStrategy(reactive,
                                                    proactiveStrategy(pressureGauge, arcMemEngine, contextUnitRepository,
                                                                      canonizationGate, invariantEvaluator, llmCallService));
            }
        };
    }

    private ProactiveMaintenanceStrategy proactiveStrategy(
            MemoryPressureGauge pressureGauge,
            ArcMemEngine arcMemEngine,
            MemoryUnitRepository contextUnitRepository,
            CanonizationGate canonizationGate,
            InvariantEvaluator invariantEvaluator,
            LlmCallService llmCallService) {
        return new ProactiveMaintenanceStrategy(pressureGauge, arcMemEngine, contextUnitRepository,
                                                canonizationGate, invariantEvaluator, llmCallService, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    DuplicateDetector duplicateDetector(ChatModel chatModel, LlmCallService llmCallService) {
        var fast = new NormalizedStringDuplicateDetector();
        var llm = new LlmDuplicateDetector(chatModel, llmCallService);
        return switch (properties.unit().dedupStrategy()) {
            case FAST_ONLY -> {
                logger.info("Using fast-only duplicate detection (normalized string)");
                yield fast;
            }
            case LLM_ONLY -> {
                logger.info("Using LLM-only duplicate detection");
                yield llm;
            }
            case FAST_THEN_LLM -> {
                logger.info("Using composite duplicate detection (fast then LLM)");
                yield new CompositeDuplicateDetector(fast, llm);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    RelevanceScorer relevanceScorer(ChatModel chatModel, LlmCallService llmCallService) {
        return new RelevanceScorer(chatModel, properties.conflictDetection().model(), llmCallService);
    }
}
