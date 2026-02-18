package dev.dunnam.diceanchors;

import com.embabel.common.ai.model.DefaultModelSelectionCriteria;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.common.ai.model.ModelProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
class EmbeddingConfiguration {

    @Bean
    @Primary
    EmbeddingService embeddingService(ModelProvider modelProvider) {
        return modelProvider.getEmbeddingService(DefaultModelSelectionCriteria.INSTANCE);
    }
}
