package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.sim.engine.LlmCallService;
import jakarta.annotation.PostConstruct;
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

    @PostConstruct
    void validateConfiguration() {
        var anchor = properties.anchor();
        var assembly = properties.assembly();

        if (anchor.budget() <= 0)
            throw new IllegalStateException("dice-anchors.anchor.budget must be > 0, got: " + anchor.budget());
        if (anchor.autoActivateThreshold() < 0.0 || anchor.autoActivateThreshold() > 1.0)
            throw new IllegalStateException("dice-anchors.anchor.autoActivateThreshold must be in [0.0, 1.0], got: " + anchor.autoActivateThreshold());
        if (anchor.minRank() >= anchor.maxRank())
            throw new IllegalStateException("dice-anchors.anchor.minRank must be < maxRank, got minRank=" + anchor.minRank() + " maxRank=" + anchor.maxRank());
        if (anchor.initialRank() < anchor.minRank() || anchor.initialRank() > anchor.maxRank())
            throw new IllegalStateException("dice-anchors.anchor.initialRank must be in [minRank, maxRank]");
        if (anchor.demoteThreshold() < 0.0 || anchor.demoteThreshold() > 1.0)
            throw new IllegalStateException("dice-anchors.anchor.demoteThreshold must be in [0.0, 1.0], got: " + anchor.demoteThreshold());
        if (assembly.promptTokenBudget() < 0)
            throw new IllegalStateException("dice-anchors.assembly.promptTokenBudget must be >= 0, got: " + assembly.promptTokenBudget());
    }

    @Bean
    ConflictDetector conflictDetector(ChatModel chatModel, LlmCallService llmCallService) {
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
                        new LlmConflictDetector(chatModel, properties.conflictDetection().model(),
                                llmCallService),
                        new SubjectFilter(),
                        ConflictDetectionStrategy.LEXICAL_THEN_SEMANTIC
                );
            }
            default -> {
                logger.info("Using LLM-based conflict detection");
                yield new LlmConflictDetector(chatModel, properties.conflictDetection().model(),
                        llmCallService);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    ConflictResolver conflictResolver() {
        return ConflictResolver.byAuthority();
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
}
