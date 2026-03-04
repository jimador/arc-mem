package dev.dunnam.diceanchors.assembly;

/**
 * Strategy interface for validating LLM responses against active anchors.
 * <p>
 * <h2>Enforcement spectrum</h2>
 * Three tiers of increasing strength and cost:
 * <ol>
 *   <li><strong>Prompt injection</strong> (zero cost) — anchors are injected into the
 *       system prompt; the LLM is trusted to comply. See {@link PromptInjectionEnforcer}.</li>
 *   <li><strong>Post-generation validation</strong> (one LLM call) — the response is
 *       evaluated against enforced anchors after generation; violations trigger
 *       RETRY or REJECT. See {@link PostGenerationValidator}.</li>
 *   <li><strong>Constrained decoding</strong> (integrated, future F12) — generation is
 *       guided at inference time so constraint violations are structurally impossible.
 *       Inspired by the Google AI STATIC paper (2024), which demonstrated sparse matrix
 *       constrained decoding for deterministic structured output generation. Requires
 *       local model infrastructure.</li>
 * </ol>
 *
 * <h2>Extensibility</h2>
 * This interface is intentionally non-sealed to allow future implementations:
 * Prolog invariant inference, logit bias adjustment, constrained decoding, etc.
 * Add new strategies without changing this API.
 *
 * <h2>Thread safety</h2>
 * All implementations MUST be thread-safe. {@code enforce()} may be called concurrently
 * from multiple simulation threads or chat sessions.
 */
@FunctionalInterface
public interface ComplianceEnforcer {

    /**
     * Validates {@code context.responseText()} against the active anchors and policy
     * in {@code context}, returning a result that includes any violations found and
     * the suggested action for the caller.
     *
     * @param context everything needed to perform enforcement
     * @return validation result; never null
     */
    ComplianceResult enforce(ComplianceContext context);
}
