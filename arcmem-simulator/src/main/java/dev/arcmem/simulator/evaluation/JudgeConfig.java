package dev.arcmem.simulator.evaluation;

public record JudgeConfig(
        String systemPromptPath,
        int confidenceThreshold,
        String label
) {
    private static final String OPEN_PROMPT = "prompts/drift-evaluation-system-open.jinja";
    private static final String HARDENED_PROMPT = "prompts/drift-evaluation-system.jinja";

    public static JudgeConfig open() {
        return new JudgeConfig(OPEN_PROMPT, 0, "open");
    }

    public static JudgeConfig hardened() {
        return new JudgeConfig(HARDENED_PROMPT, 2, "hardened");
    }
}
