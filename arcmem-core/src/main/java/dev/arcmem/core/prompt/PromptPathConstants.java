package dev.arcmem.core.prompt;

/**
 * Classpath-relative paths to all Jinja2 prompt templates used by this application.
 *
 * <p>All constants are classpath-relative strings suitable for use with
 * {@link PromptTemplates#load(String)} and {@link PromptTemplates#render(String, java.util.Map)}.
 */
public final class PromptPathConstants {

    private PromptPathConstants() {
    }

    public static final String UNITS_REFERENCE =
            "prompts/units-reference.jinja";

    public static final String UNITS_REFERENCE_OVERHEAD =
            "prompts/units-reference-overhead.jinja";

    public static final String UNIT_TEMPLATE_PROVISIONAL =
            "prompts/unit-tier-provisional.jinja";

    public static final String UNIT_TEMPLATE_UNRELIABLE =
            "prompts/unit-tier-unreliable.jinja";

    public static final String UNIT_TEMPLATE_RELIABLE =
            "prompts/unit-tier-reliable.jinja";

    public static final String UNIT_TEMPLATE_CANON =
            "prompts/unit-tier-canon.jinja";

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

}
