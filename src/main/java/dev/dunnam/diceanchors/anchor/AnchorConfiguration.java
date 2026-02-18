package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
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

    @Bean
    ConflictDetector conflictDetector(ChatModel chatModel, DiceAnchorsProperties properties) {
        var strategyName = properties.conflictDetection().strategy();
        return switch (strategyName.toLowerCase()) {
            case "lexical" -> {
                logger.info("Using lexical-only conflict detection");
                yield new NegationConflictDetector();
            }
            case "hybrid" -> {
                logger.info("Using hybrid conflict detection (lexical + semantic with subject filter)");
                yield new CompositeConflictDetector(
                        new NegationConflictDetector(),
                        new LlmConflictDetector(chatModel, properties.conflictDetection().model()),
                        new SubjectFilter(),
                        ConflictDetectionStrategy.LEXICAL_THEN_SEMANTIC
                );
            }
            default -> {
                logger.info("Using LLM-based conflict detection");
                yield new LlmConflictDetector(chatModel, properties.conflictDetection().model());
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    ConflictResolver conflictResolver() {
        return new AuthorityConflictResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    ReinforcementPolicy reinforcementPolicy() {
        return new ThresholdReinforcementPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    DecayPolicy decayPolicy() {
        return new ExponentialDecayPolicy(24.0);
    }
}
