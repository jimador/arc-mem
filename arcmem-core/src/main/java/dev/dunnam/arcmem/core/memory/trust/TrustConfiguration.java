package dev.dunnam.arcmem.core.memory.trust;
import dev.dunnam.arcmem.core.memory.budget.*;
import dev.dunnam.arcmem.core.memory.canon.*;
import dev.dunnam.arcmem.core.memory.conflict.*;
import dev.dunnam.arcmem.core.memory.engine.*;
import dev.dunnam.arcmem.core.memory.maintenance.*;
import dev.dunnam.arcmem.core.memory.model.*;
import dev.dunnam.arcmem.core.memory.mutation.*;
import dev.dunnam.arcmem.core.memory.trust.*;
import dev.dunnam.arcmem.core.assembly.budget.*;
import dev.dunnam.arcmem.core.assembly.compaction.*;
import dev.dunnam.arcmem.core.assembly.compliance.*;
import dev.dunnam.arcmem.core.assembly.protection.*;
import dev.dunnam.arcmem.core.assembly.retrieval.*;

import dev.dunnam.arcmem.core.config.ArcMemProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Declares {@link TrustSignal} implementations and the {@link TrustPipeline} as Spring beans.
 * Signals registered here are auto-collected by Spring into the {@code List<TrustSignal>}
 * injected into {@link TrustPipeline}.
 */
@Configuration
class TrustConfiguration {

    @Bean
    TrustSignal corroborationSignal() {
        return TrustSignal.corroboration();
    }

    @Bean
    TrustSignal extractionConfidenceSignal() {
        return TrustSignal.extractionConfidence();
    }

    @Bean
    GraphConsistencySignal graphConsistencySignal(ArcMemEngine arcMemEngine) {
        return new GraphConsistencySignal(arcMemEngine);
    }

    @Bean
    NoveltySignal noveltySignal(ArcMemEngine arcMemEngine, ArcMemProperties properties) {
        return new NoveltySignal(arcMemEngine, properties);
    }

    @Bean
    ImportanceSignal importanceSignal(ArcMemEngine arcMemEngine, ArcMemProperties properties) {
        return new ImportanceSignal(arcMemEngine, properties);
    }

    @Bean
    TrustPipeline trustPipeline(List<TrustSignal> signals) {
        return new TrustPipeline(signals);
    }
}
