package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the active {@link BudgetStrategy} bean from {@code dice-anchors.budget.strategy}.
 * <p>
 * Default strategy is {@link BudgetStrategyType#COUNT}, which reproduces the original
 * inline behavior with no behavioral change. {@link BudgetStrategyType#INTERFERENCE_DENSITY}
 * is opt-in and requires {@link ConflictIndex} to provide meaningful density scores.
 */
@Configuration
public class BudgetStrategyConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(BudgetStrategyConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(BudgetStrategy.class)
    BudgetStrategy budgetStrategy(DiceAnchorsProperties properties, ConflictIndex conflictIndex) {
        var budgetConfig = properties.budget();
        var strategyType = budgetConfig != null ? budgetConfig.strategy() : BudgetStrategyType.COUNT;
        return switch (strategyType) {
            case COUNT -> {
                logger.info("Using count-based budget strategy (default)");
                yield new CountBasedBudgetStrategy();
            }
            case INTERFERENCE_DENSITY -> {
                var warningThreshold = budgetConfig.densityWarningThreshold();
                var reductionThreshold = budgetConfig.densityReductionThreshold();
                var reductionFactor = budgetConfig.densityReductionFactor();
                logger.info("Using interference-density budget strategy (warning={}, reduction={}, factor={})",
                        warningThreshold, reductionThreshold, reductionFactor);
                yield new InterferenceDensityBudgetStrategy(
                        new ConnectedComponentsCalculator(),
                        conflictIndex,
                        warningThreshold,
                        reductionThreshold,
                        reductionFactor);
            }
        };
    }

    /**
     * Factory for creating per-context {@link BudgetStrategy} instances from a
     * {@link BudgetStrategyType}. Used by the simulation layer to apply per-scenario
     * strategy overrides without coupling the factory logic to the simulation package.
     */
    @Bean
    BudgetStrategyFactory budgetStrategyFactory(DiceAnchorsProperties properties, ConflictIndex conflictIndex) {
        return strategyType -> {
            if (strategyType == null || strategyType == BudgetStrategyType.COUNT) {
                return new CountBasedBudgetStrategy();
            }
            var budgetConfig = properties.budget();
            var warningThreshold = budgetConfig != null ? budgetConfig.densityWarningThreshold() : 0.6;
            var reductionThreshold = budgetConfig != null ? budgetConfig.densityReductionThreshold() : 0.8;
            var reductionFactor = budgetConfig != null ? budgetConfig.densityReductionFactor() : 0.5;
            return new InterferenceDensityBudgetStrategy(
                    new ConnectedComponentsCalculator(),
                    conflictIndex,
                    warningThreshold,
                    reductionThreshold,
                    reductionFactor);
        };
    }
}
