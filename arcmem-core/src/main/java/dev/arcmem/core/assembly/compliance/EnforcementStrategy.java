package dev.arcmem.core.assembly.compliance;

/**
 * Selects which {@link ComplianceEnforcer} implementation governs an enforcement context.
 * <p>
 * Default is {@link #PROMPT_ONLY}, which preserves pre-feature behavior.
 */
public enum EnforcementStrategy {

    /**
     * Trust the LLM to comply with injected units. Zero overhead.
     */
    PROMPT_ONLY,

    /**
     * Translate CANON/RELIABLE units to OpenAI logit_bias parameters before generation.
     */
    LOGIT_BIAS,

    /**
     * Compose prompt injection, logit bias, and post-generation validation in layers.
     */
    HYBRID
}
