package dev.dunnam.diceanchors.persistence;

/**
 * Intermediate result from vector similarity search containing proposition ID and score.
 */
record PropositionSimilarityResult(String id, double score) {
}
