package dev.dunnam.diceanchors.assembly;

/**
 * Interface for constrained decoding enforcement — the third tier of the compliance spectrum.
 * <p>
 * <h2>Contract</h2>
 * At each decoding step, tokens where {@link ConstraintMask#allowedTokens()}{@code [i] == false}
 * have their logits set to negative infinity, making constraint violations structurally impossible
 * to generate. This is the STATIC architecture (Google AI STATIC, 2024) adapted to the
 * dice-anchors domain.
 *
 * <h2>Implementation status</h2>
 * Full constrained decoding requires access to the raw logit distribution at each decoding step,
 * which is unavailable through API-based models (OpenAI, Anthropic). Implementation requires
 * local model infrastructure (vLLM custom samplers, Hugging Face {@code LogitsProcessor}).
 * This interface defines the contract for when that infrastructure is available.
 *
 * <h2>Current stub</h2>
 * {@link NoOpConstrainedDecodingEnforcer} returns an unconstrained mask (all tokens allowed)
 * and a compliant result. It satisfies the interface contract for testing without requiring
 * local model infrastructure.
 */
public interface ConstrainedDecodingEnforcer extends ComplianceEnforcer {

    /**
     * Computes a vocabulary mask for the given anchor constraints and vocabulary size.
     * <p>
     * The returned mask is applied at each decoding step: tokens at positions where
     * {@code allowedTokens[i] == false} are suppressed (logit set to negative infinity).
     *
     * @param index     constraint index built from active CANON/RELIABLE anchors
     * @param vocabSize total vocabulary size; defines the length of the returned mask
     * @return a constraint mask covering the full vocabulary
     */
    ConstraintMask computeConstraintMask(AnchorConstraintIndex index, int vocabSize);
}
