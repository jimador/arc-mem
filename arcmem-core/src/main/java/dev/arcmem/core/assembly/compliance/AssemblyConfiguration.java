package dev.arcmem.core.assembly.compliance;

import dev.arcmem.core.assembly.budget.CharHeuristicTokenCounter;
import dev.arcmem.core.assembly.budget.TokenCounter;
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
