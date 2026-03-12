package dev.dunnam.diceanchors.prompt;

/**
 * Classpath-relative paths to all Jinja2 prompt templates used by this application.
 *
 * <p>All constants are classpath-relative strings suitable for use with
 * {@link PromptTemplates#load(String)} and {@link PromptTemplates#render(String, java.util.Map)}.
 */
public final class PromptPathConstants {

    private PromptPathConstants() {
    }

    public static final String ANCHORS_REFERENCE =
            "prompts/anchors-reference.jinja";

    public static final String ANCHORS_REFERENCE_OVERHEAD =
            "prompts/anchors-reference-overhead.jinja";

    public static final String ANCHOR_TEMPLATE_PROVISIONAL =
            "prompts/anchor-tier-provisional.jinja";

    public static final String ANCHOR_TEMPLATE_UNRELIABLE =
            "prompts/anchor-tier-unreliable.jinja";

    public static final String ANCHOR_TEMPLATE_RELIABLE =
            "prompts/anchor-tier-reliable.jinja";

    public static final String ANCHOR_TEMPLATE_CANON =
            "prompts/anchor-tier-canon.jinja";

    public static final String PROPOSITIONS_REFERENCE =
            "prompts/propositions-reference.jinja";

    public static final String DRIFT_EVALUATION_SYSTEM =
            "prompts/drift-evaluation-system.jinja";

    public static final String RELEVANCE_SCORING =
            "prompts/relevance-scoring.jinja";

    public static final String DICE_BATCH_CONFLICT_DETECTION =
            "prompts/dice/batch-conflict-detection.jinja";

    public static final String DICE_CONFLICT_DETECTION =
            "prompts/dice/conflict-detection.jinja";

    public static final String DICE_BATCH_DUPLICATE_SYSTEM =
            "prompts/dice/batch-duplicate-system.jinja";

    public static final String DICE_BATCH_DUPLICATE_USER =
            "prompts/dice/batch-duplicate-user.jinja";

    public static final String DICE_DUPLICATE_SYSTEM =
            "prompts/dice/duplicate-system.jinja";

    public static final String DICE_DUPLICATE_USER =
            "prompts/dice/duplicate-user.jinja";

    public static final String SIM_SYSTEM =
            "prompts/sim/system.jinja";

    public static final String SIM_USER =
            "prompts/sim/user.jinja";

    public static final String SIM_ADVERSARIAL_REQUEST =
            "prompts/sim/adversarial-request.jinja";

    public static final String SIM_ADVERSARIAL_FALLBACK_MESSAGE =
            "prompts/sim/adversarial-fallback-message.jinja";

    public static final String SIM_DEFAULT_PLAYER_MESSAGE =
            "prompts/sim/default-player-message.jinja";

    public static final String SIM_WARMUP_PLAYER_MESSAGE =
            "prompts/sim/warmup-player-message.jinja";

    public static final String SIM_DRIFT_EVALUATION_USER =
            "prompts/sim/drift-evaluation-user.jinja";

    public static final String SIM_CONVERSATION_LINE =
            "prompts/sim/conversation-line.jinja";

    public static final String SIM_SUMMARY =
            "prompts/sim/summary.jinja";
}
