package dev.dunnam.diceanchors.anchor;

/**
 * Routing zone for proposition trust evaluation.
 * Determines whether a proposition is automatically promoted,
 * queued for review, or archived.
 */
public enum PromotionZone {
    AUTO_PROMOTE,
    REVIEW,
    ARCHIVE
}
