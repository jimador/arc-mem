package dev.arcmem.core.memory.canon;
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

@Configuration
public class ComplianceConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceConfiguration.class);

    @Bean
    public CompliancePolicy compliancePolicy(ArcMemProperties properties) {
        return switch (properties.unit().compliancePolicy()) {
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
