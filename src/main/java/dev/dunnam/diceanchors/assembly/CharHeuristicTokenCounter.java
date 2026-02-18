package dev.dunnam.diceanchors.assembly;

/**
 * Default heuristic token counter using approximately 4 characters per token.
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
