package dev.dunnam.diceanchors.assembly;

/**
 * Heuristic {@link TokenCounter} that estimates token count using a fixed ratio
 * of 4 characters per token.
 * <p>
 * This is a rough approximation suitable for development and simulation workloads.
 * The 4 chars/token heuristic is derived from the average English word length plus
 * a space, and is commonly cited as a reasonable first-order estimate for GPT-family
 * models on English prose. Actual token counts will vary significantly based on
 * language, punctuation density, and model-specific vocabulary.
 * <p>
 * <strong>Note:</strong> For production use or precise budget enforcement, replace
 * this implementation with a model-specific tokenizer (e.g., tiktoken for OpenAI
 * models, or the model provider's native token-counting API).
 * <p>
 * This class is thread-safe: it holds no mutable state.
 */
public class CharHeuristicTokenCounter implements TokenCounter {

    private static final int CHARS_PER_TOKEN = 4;

    @Override
    public int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() / CHARS_PER_TOKEN;
    }
}
