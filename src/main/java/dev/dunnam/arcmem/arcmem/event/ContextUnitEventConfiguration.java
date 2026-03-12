package dev.dunnam.diceanchors.anchor.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AnchorEventConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AnchorLifecycleListener anchorLifecycleListener() {
        return new AnchorLifecycleListener();
    }
}
