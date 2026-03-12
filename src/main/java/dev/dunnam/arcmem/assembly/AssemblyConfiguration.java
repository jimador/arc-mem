package dev.dunnam.diceanchors.assembly;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Default assembly component configuration.
 */
@Configuration
public class AssemblyConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TokenCounter tokenCounter() {
        return new CharHeuristicTokenCounter();
    }
}
