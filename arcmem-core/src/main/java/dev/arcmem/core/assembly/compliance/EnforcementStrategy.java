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

/**
 * Selects which {@link ComplianceEnforcer} implementation governs an enforcement context.
 * <p>
 * Default is {@link #PROMPT_ONLY}, which preserves pre-feature behavior.
 */
public enum EnforcementStrategy {

    /** Trust the LLM to comply with injected units. Zero overhead. */
    PROMPT_ONLY,

    /** Translate CANON/RELIABLE units to OpenAI logit_bias parameters before generation. */
    LOGIT_BIAS,

    /** Compose prompt injection, logit bias, and post-generation validation in layers. */
    HYBRID
}
