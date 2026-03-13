package dev.arcmem.core.assembly.compliance;
import dev.arcmem.core.memory.budget.*;
import dev.arcmem.core.memory.canon.*;
import dev.arcmem.core.memory.conflict.*;
import dev.arcmem.core.memory.engine.*;
import dev.arcmem.core.memory.maintenance.*;
import dev.arcmem.core.memory.model.*;
import dev.arcmem.core.memory.mutation.*;
import dev.arcmem.core.memory.trust.*;
import dev.arcmem.core.assembly.budget.*;
import dev.arcmem.core.assembly.compaction.*;
import dev.arcmem.core.assembly.compliance.*;
import dev.arcmem.core.assembly.protection.*;
import dev.arcmem.core.assembly.retrieval.*;

import dev.arcmem.core.config.ArcMemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Spring configuration that selects the active {@link ComplianceEnforcer} based on
 * the configured {@link EnforcementStrategy}.
 * <p>
 * Default strategy is {@link EnforcementStrategy#PROMPT_ONLY}, which preserves
 * pre-feature behavior. No behavioral change without explicit opt-in via
 * {@code arc-mem.assembly.enforcement-strategy}.
 */
@Configuration
public class ComplianceEnforcerFactory {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceEnforcerFactory.class);

    private final ArcMemProperties properties;
    private final PromptInjectionEnforcer promptInjectionEnforcer;
    private final LogitBiasEnforcer logitBiasEnforcer;
    private final PostGenerationValidator postGenerationValidator;

    public ComplianceEnforcerFactory(
            ArcMemProperties properties,
            PromptInjectionEnforcer promptInjectionEnforcer,
            LogitBiasEnforcer logitBiasEnforcer,
            PostGenerationValidator postGenerationValidator) {
        this.properties = properties;
        this.promptInjectionEnforcer = promptInjectionEnforcer;
        this.logitBiasEnforcer = logitBiasEnforcer;
        this.postGenerationValidator = postGenerationValidator;
    }

    @Bean
    @Primary
    public ComplianceEnforcer complianceEnforcer() {
        var strategy = effectiveStrategy();
        logger.info("compliance.strategy.configured={}", strategy);
        return switch (strategy) {
            case PROMPT_ONLY -> promptInjectionEnforcer;
            case LOGIT_BIAS -> logitBiasEnforcer;
            case HYBRID -> new HybridComplianceEnforcer(
                    List.of(promptInjectionEnforcer, logitBiasEnforcer, postGenerationValidator));
        };
    }

    private EnforcementStrategy effectiveStrategy() {
        var assembly = properties.assembly();
        if (assembly == null || assembly.enforcementStrategy() == null) {
            return EnforcementStrategy.PROMPT_ONLY;
        }
        return assembly.enforcementStrategy();
    }
}
