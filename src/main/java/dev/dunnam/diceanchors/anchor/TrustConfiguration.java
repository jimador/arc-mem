package dev.dunnam.diceanchors.anchor;

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
    TrustSignal sourceAuthoritySignal() {
        return TrustSignal.sourceAuthority();
    }

    @Bean
    GraphConsistencySignal graphConsistencySignal(AnchorEngine anchorEngine) {
        return new GraphConsistencySignal(anchorEngine);
    }

    @Bean
    TrustPipeline trustPipeline(List<TrustSignal> signals) {
        return new TrustPipeline(signals);
    }
}
