package dev.arcmem.core.memory.budget;

import dev.arcmem.core.config.ArcMemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BudgetStrategyConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(BudgetStrategyConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(BudgetStrategy.class)
    BudgetStrategy budgetStrategy(ArcMemProperties properties) {
        logger.info("Using count-based budget strategy (default)");
        return new CountBasedBudgetStrategy();
    }

    @Bean
    BudgetStrategyFactory budgetStrategyFactory() {
        return strategyType -> new CountBasedBudgetStrategy();
    }
}
