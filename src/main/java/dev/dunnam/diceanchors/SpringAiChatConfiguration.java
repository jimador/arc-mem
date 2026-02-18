package dev.dunnam.diceanchors;

import com.embabel.common.ai.model.LlmOptions;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;

@Configuration
class SpringAiChatConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OpenAiApi openAiApi(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.base-url:https://api.openai.com}") String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("spring.ai.openai.api-key is required to create ChatModel.");
        }
        return OpenAiApi.builder()
                        .apiKey(apiKey)
                        .baseUrl(baseUrl)
                        .build();
    }

    @Bean
    @ConditionalOnMissingBean
    OpenAiChatOptions openAiChatOptions(DiceAnchorsProperties properties) {
        var options = new OpenAiChatOptions();
        var chatLlm = properties.chat() != null ? properties.chat().chatLlm() : null;
        if (chatLlm != null) {
            applyChatOptions(options, chatLlm);
        }
        return options;
    }

    @Bean
    @ConditionalOnMissingBean
    ToolCallingManager toolCallingManager(Optional<ObservationRegistry> observationRegistry) {
        var registry = observationRegistry.orElse(ObservationRegistry.NOOP);
        return DefaultToolCallingManager.builder()
                                        .observationRegistry(registry)
                                        .toolCallbackResolver(new StaticToolCallbackResolver(List.of()))
                                        .toolExecutionExceptionProcessor(DefaultToolExecutionExceptionProcessor.builder().build())
                                        .build();
    }

    @Bean
    @ConditionalOnMissingBean
    ChatModel chatModel(
            OpenAiApi openAiApi,
            OpenAiChatOptions openAiChatOptions,
            ToolCallingManager toolCallingManager,
            Optional<ObservationRegistry> observationRegistry) {
        var builder = OpenAiChatModel.builder()
                                     .openAiApi(openAiApi)
                                     .defaultOptions(openAiChatOptions)
                                     .toolCallingManager(toolCallingManager);
        observationRegistry.ifPresent(builder::observationRegistry);
        return builder.build();
    }

    private void applyChatOptions(OpenAiChatOptions options, LlmOptions chatLlm) {
        var model = chatLlm.getModel();
        if (model != null && !model.isBlank()) {
            options.setModel(model);
        }
        var temperature = chatLlm.getTemperature();
        if (temperature != null) {
            options.setTemperature(temperature);
        }
        var maxTokens = chatLlm.getMaxTokens();
        if (maxTokens != null) {
            options.setMaxTokens(maxTokens);
        }
        var topP = chatLlm.getTopP();
        if (topP != null) {
            options.setTopP(topP);
        }
        var frequencyPenalty = chatLlm.getFrequencyPenalty();
        if (frequencyPenalty != null) {
            options.setFrequencyPenalty(frequencyPenalty);
        }
        var presencePenalty = chatLlm.getPresencePenalty();
        if (presencePenalty != null) {
            options.setPresencePenalty(presencePenalty);
        }
    }
}
