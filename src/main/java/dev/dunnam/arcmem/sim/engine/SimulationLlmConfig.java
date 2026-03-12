package dev.dunnam.diceanchors.sim.engine;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * Wires {@link ChatModelHolder} with observation support for the simulation engine.
 */
@Configuration
public class SimulationLlmConfig {

    @Bean
    ChatModelHolder chatModelHolder(ChatModel chatModel, Optional<ObservationRegistry> observationRegistry) {
        return new ChatModelHolder(chatModel, observationRegistry.orElse(ObservationRegistry.NOOP));
    }
}
