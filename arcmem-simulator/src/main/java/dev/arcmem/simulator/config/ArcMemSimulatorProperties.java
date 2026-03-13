package dev.arcmem.simulator.config;

import com.embabel.common.ai.model.LlmOptions;
import dev.arcmem.simulator.history.RunHistoryStoreType;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "arc-mem")
public record ArcMemSimulatorProperties(
        @Nullable @NestedConfigurationProperty ChatConfig chat,
        @Nullable @NestedConfigurationProperty SimConfig sim,
        @Nullable @NestedConfigurationProperty RunHistoryConfig runHistory
) {

    public record ChatConfig(
            @DefaultValue("assistant") String persona,
            @DefaultValue("200") int maxWords,
            @NestedConfigurationProperty LlmOptions chatLlm
    ) {}

    public record SimConfig(
            @DefaultValue("gpt-4.1-mini") String evaluatorModel,
            @DefaultValue("30") int adversaryBudget,
            @DefaultValue("true") boolean parallelPostResponse,
            @DefaultValue("4") int benchmarkParallelism
    ) {}

    public record RunHistoryConfig(
            @DefaultValue("MEMORY") RunHistoryStoreType store
    ) {}
}
