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
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides default implementations of anchor engine strategy interfaces.
 * All beans are conditional so applications can override them by registering
 * their own implementations.
 */
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
        var strategyName = properties.conflictDetection().strategy();
        var conflict = properties.conflict();
        var overlapThreshold = conflict != null ? conflict.negationOverlapThreshold() : 0.5;
        var llmConfidence = conflict != null ? conflict.llmConfidence() : 0.9;
        return switch (strategyName.toLowerCase()) {
            case "lexical" -> {
                logger.info("Using lexical-only conflict detection (overlap threshold: {})", overlapThreshold);
                yield new NegationConflictDetector(overlapThreshold);
            }
            case "hybrid" -> {
                logger.info("Using hybrid conflict detection (lexical + semantic with subject filter)");
                yield new CompositeConflictDetector(
                        new NegationConflictDetector(overlapThreshold),
                        new LlmConflictDetector(llmConfidence, chatModel, properties.conflictDetection().model(),
                                llmCallService),
                        new SubjectFilter(),
                        ConflictDetectionStrategy.LEXICAL_THEN_SEMANTIC
                );
            }
            default -> {
                logger.info("Using LLM-based conflict detection (confidence: {})", llmConfidence);
                yield new LlmConflictDetector(llmConfidence, chatModel, properties.conflictDetection().model(),
                        llmCallService);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    ConflictResolver conflictResolver() {
        var conflict = properties.conflict();
        if (conflict != null) {
            return new AuthorityConflictResolver(
                    conflict.replaceThreshold(),
                    conflict.demoteThreshold(),
                    conflict.tier()
            );
        }
        return new AuthorityConflictResolver();
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
        var strategy = properties.anchor().dedupStrategy().toUpperCase();
        var fast = new NormalizedStringDuplicateDetector();
        var llm = new LlmDuplicateDetector(chatModel, llmCallService);
        return switch (strategy) {
            case "FAST_ONLY" -> {
                logger.info("Using fast-only duplicate detection (normalized string)");
                yield fast;
            }
            case "LLM_ONLY" -> {
                logger.info("Using LLM-only duplicate detection");
                yield llm;
            }
            default -> {
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
