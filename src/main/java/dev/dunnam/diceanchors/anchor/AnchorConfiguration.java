package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.assembly.RelevanceScorer;
import dev.dunnam.diceanchors.extract.CompositeDuplicateDetector;
import dev.dunnam.diceanchors.extract.DuplicateDetector;
import dev.dunnam.diceanchors.extract.LlmDuplicateDetector;
import dev.dunnam.diceanchors.extract.NormalizedStringDuplicateDetector;
import dev.dunnam.diceanchors.sim.engine.LlmCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(DiceAnchorsProperties.class)
public class AnchorConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(AnchorConfiguration.class);

    private final DiceAnchorsProperties properties;

    public AnchorConfiguration(DiceAnchorsProperties properties) {
        this.properties = properties;
    }

    @Bean
    ConflictDetector conflictDetector(ChatModel chatModel, LlmCallService llmCallService) {
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
                        ConflictDetectionStrategy.LEXICAL_THEN_SEMANTIC
                );
            }
            case LLM -> {
                logger.info("Using LLM-based conflict detection (confidence: {})", conflict.llmConfidence());
                yield new LlmConflictDetector(conflict.llmConfidence(), chatModel, detection.model(),
                        llmCallService);
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
            prefix = "dice-anchors.anchor.revision",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    ConflictResolver revisionAwareConflictResolver(AuthorityConflictResolver authorityResolver,
                                                    AnchorMutationStrategy mutationStrategy) {
        var revision = properties.anchor().revision();
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
            prefix = "dice-anchors.anchor.revision",
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
    DuplicateDetector duplicateDetector(ChatModel chatModel, LlmCallService llmCallService) {
        var fast = new NormalizedStringDuplicateDetector();
        var llm = new LlmDuplicateDetector(chatModel, llmCallService);
        return switch (properties.anchor().dedupStrategy()) {
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
