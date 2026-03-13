package dev.arcmem.core.memory.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MemoryUnitEventConfiguration {

    @Bean
    @ConditionalOnMissingBean
    MemoryUnitLifecycleListener unitLifecycleListener() {
        return new MemoryUnitLifecycleListener();
    }
}
