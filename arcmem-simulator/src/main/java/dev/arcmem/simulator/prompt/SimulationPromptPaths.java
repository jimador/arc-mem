package dev.arcmem.simulator.prompt;

public final class SimulationPromptPaths {

    private SimulationPromptPaths() {}

    public static final String SYSTEM = "prompts/sim/system.jinja";
    public static final String USER = "prompts/sim/user.jinja";
    public static final String ADVERSARIAL_REQUEST = "prompts/sim/adversarial-request.jinja";
    public static final String ADVERSARIAL_FALLBACK_MESSAGE = "prompts/sim/adversarial-fallback-message.jinja";
    public static final String DEFAULT_PLAYER_MESSAGE = "prompts/sim/default-player-message.jinja";
    public static final String WARMUP_PLAYER_MESSAGE = "prompts/sim/warmup-player-message.jinja";
    public static final String DRIFT_EVALUATION_USER = "prompts/sim/drift-evaluation-user.jinja";
    public static final String CONVERSATION_LINE = "prompts/sim/conversation-line.jinja";
    public static final String SUMMARY = "prompts/sim/summary.jinja";
}
