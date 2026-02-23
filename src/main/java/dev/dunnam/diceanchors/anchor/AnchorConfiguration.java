package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.assembly.RelevanceScorer;
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

        var tier = anchor.tier();
        if (tier != null) {
            if (tier.hotThreshold() <= tier.warmThreshold())
                throw new IllegalStateException("dice-anchors.anchor.tier.hotThreshold must be > warmThreshold, got hotThreshold=" + tier.hotThreshold() + " warmThreshold=" + tier.warmThreshold());
            if (tier.warmThreshold() < 100 || tier.warmThreshold() > 900)
                throw new IllegalStateException("dice-anchors.anchor.tier.warmThreshold must be in [100, 900], got: " + tier.warmThreshold());
            if (tier.hotThreshold() < 100 || tier.hotThreshold() > 900)
                throw new IllegalStateException("dice-anchors.anchor.tier.hotThreshold must be in [100, 900], got: " + tier.hotThreshold());
            if (tier.hotDecayMultiplier() <= 0)
                throw new IllegalStateException("dice-anchors.anchor.tier.hotDecayMultiplier must be > 0, got: " + tier.hotDecayMultiplier());
            if (tier.warmDecayMultiplier() <= 0)
                throw new IllegalStateException("dice-anchors.anchor.tier.warmDecayMultiplier must be > 0, got: " + tier.warmDecayMultiplier());
            if (tier.coldDecayMultiplier() <= 0)
                throw new IllegalStateException("dice-anchors.anchor.tier.coldDecayMultiplier must be > 0, got: " + tier.coldDecayMultiplier());
        }

        var conflict = properties.conflict();
        if (conflict != null) {
            if (conflict.negationOverlapThreshold() <= 0.0 || conflict.negationOverlapThreshold() > 1.0)
                throw new IllegalStateException("dice-anchors.conflict.negation-overlap-threshold must be in (0.0, 1.0], got: " + conflict.negationOverlapThreshold());
            if (conflict.llmConfidence() <= 0.0 || conflict.llmConfidence() > 1.0)
                throw new IllegalStateException("dice-anchors.conflict.llm-confidence must be in (0.0, 1.0], got: " + conflict.llmConfidence());
            if (conflict.replaceThreshold() <= 0.0 || conflict.replaceThreshold() > 1.0)
                throw new IllegalStateException("dice-anchors.conflict.replace-threshold must be in (0.0, 1.0], got: " + conflict.replaceThreshold());
            if (conflict.demoteThreshold() <= 0.0 || conflict.demoteThreshold() > 1.0)
                throw new IllegalStateException("dice-anchors.conflict.demote-threshold must be in (0.0, 1.0], got: " + conflict.demoteThreshold());
            if (conflict.replaceThreshold() <= conflict.demoteThreshold())
                throw new IllegalStateException("dice-anchors.conflict.replace-threshold must be > demote-threshold, got replace=" + conflict.replaceThreshold() + " demote=" + conflict.demoteThreshold());
            var tierMod = conflict.tier();
            if (tierMod != null) {
                if (tierMod.hotDefenseModifier() < -0.5 || tierMod.hotDefenseModifier() > 0.5)
                    throw new IllegalStateException("dice-anchors.conflict.tier.hot-defense-modifier must be in [-0.5, 0.5], got: " + tierMod.hotDefenseModifier());
                if (tierMod.warmDefenseModifier() < -0.5 || tierMod.warmDefenseModifier() > 0.5)
                    throw new IllegalStateException("dice-anchors.conflict.tier.warm-defense-modifier must be in [-0.5, 0.5], got: " + tierMod.warmDefenseModifier());
                if (tierMod.coldDefenseModifier() < -0.5 || tierMod.coldDefenseModifier() > 0.5)
                    throw new IllegalStateException("dice-anchors.conflict.tier.cold-defense-modifier must be in [-0.5, 0.5], got: " + tierMod.coldDefenseModifier());
            }
        }

        var retrieval = properties.retrieval();
        if (retrieval != null) {
            if (retrieval.minRelevance() < 0.0 || retrieval.minRelevance() > 1.0)
                throw new IllegalStateException("dice-anchors.retrieval.min-relevance must be in [0.0, 1.0], got: " + retrieval.minRelevance());
            if (retrieval.baselineTopK() <= 0)
                throw new IllegalStateException("dice-anchors.retrieval.baseline-top-k must be > 0, got: " + retrieval.baselineTopK());
            if (retrieval.toolTopK() <= 0)
                throw new IllegalStateException("dice-anchors.retrieval.tool-top-k must be > 0, got: " + retrieval.toolTopK());
            var scoring = retrieval.scoring();
            if (scoring != null) {
                var weightSum = scoring.authorityWeight() + scoring.tierWeight() + scoring.confidenceWeight();
                if (Math.abs(weightSum - 1.0) > 0.001)
                    throw new IllegalStateException("dice-anchors.retrieval.scoring weights must sum to 1.0, got: " + weightSum);
                if (scoring.authorityWeight() < 0.0 || scoring.authorityWeight() > 1.0)
                    throw new IllegalStateException("dice-anchors.retrieval.scoring.authority-weight must be in [0.0, 1.0], got: " + scoring.authorityWeight());
                if (scoring.tierWeight() < 0.0 || scoring.tierWeight() > 1.0)
                    throw new IllegalStateException("dice-anchors.retrieval.scoring.tier-weight must be in [0.0, 1.0], got: " + scoring.tierWeight());
                if (scoring.confidenceWeight() < 0.0 || scoring.confidenceWeight() > 1.0)
                    throw new IllegalStateException("dice-anchors.retrieval.scoring.confidence-weight must be in [0.0, 1.0], got: " + scoring.confidenceWeight());
            }
        }
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
    RelevanceScorer relevanceScorer(ChatModel chatModel, LlmCallService llmCallService) {
        return new RelevanceScorer(chatModel, properties.conflictDetection().model(), llmCallService);
    }
}
