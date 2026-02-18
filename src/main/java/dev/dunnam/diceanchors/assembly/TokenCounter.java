package dev.dunnam.diceanchors.assembly;

/**
 * Strategy interface for estimating token usage for prompt text.
 */
public interface TokenCounter {

    int estimate(String text);
}
