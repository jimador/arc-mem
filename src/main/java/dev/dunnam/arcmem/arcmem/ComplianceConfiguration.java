package dev.dunnam.diceanchors.anchor;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ComplianceConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceConfiguration.class);

    @Bean
    public CompliancePolicy compliancePolicy(DiceAnchorsProperties properties) {
        return switch (properties.anchor().compliancePolicy()) {
            case TIERED -> {
                logger.info("Using authority-tiered compliance policy");
                yield CompliancePolicy.tiered();
            }
            case FLAT -> {
                logger.info("Using default (flat) compliance policy");
                yield CompliancePolicy.flat();
            }
        };
    }
}
