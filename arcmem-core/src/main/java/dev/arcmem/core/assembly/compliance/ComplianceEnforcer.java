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
 * Strategy interface for validating LLM responses against active memory units.
 * <p>
 * <h2>Enforcement spectrum</h2>
 * Three tiers of increasing strength and cost:
 * <ol>
 *   <li><strong>Prompt injection</strong> (zero cost) — memory units are injected into the
 *       system prompt; the LLM is trusted to comply. See {@link PromptInjectionEnforcer}.</li>
 *   <li><strong>Post-generation validation</strong> (one LLM call) — the response is
 *       evaluated against enforced memory units after generation; violations trigger
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
     * Validates {@code context.responseText()} against the active memory units and policy
     * in {@code context}, returning a result that includes any violations found and
     * the suggested action for the caller.
     *
     * @param context everything needed to perform enforcement
     * @return validation result; never null
     */
    ComplianceResult enforce(ComplianceContext context);
}
