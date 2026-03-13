package dev.arcmem.core.assembly.budget;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Heuristic detector for model-specific capabilities.
 * <p>
 * Unknown models default to unsupported — safe degradation ensures no exceptions
 * are thrown for unrecognized model IDs. Known patterns:
 * <ul>
 *   <li>OpenAI {@code gpt-4*}, {@code gpt-3.5*}, {@code o1*}, {@code o3*} — logit bias supported</li>
 *   <li>Anthropic {@code claude-*} — logit bias not supported</li>
 *   <li>Unknown — defaults to unsupported</li>
 * </ul>
 */
@Component
public class ModelCapabilityDetector {

    private static final Logger logger = LoggerFactory.getLogger(ModelCapabilityDetector.class);

    /**
     * Returns true if the given model ID is known to support the OpenAI {@code logit_bias} parameter.
     * Unknown models return false (safe degradation).
     */
    public boolean supportsLogitBias(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            logger.debug("compliance.capability.logit_bias_supported=false model=unknown");
            return false;
        }
        var lower = modelId.toLowerCase();
        var supported = lower.startsWith("gpt-4")
                || lower.startsWith("gpt-3.5")
                || lower.startsWith("o1")
                || lower.startsWith("o3");
        logger.debug("compliance.capability.logit_bias_supported={} model={}", supported, modelId);
        return supported;
    }
}
