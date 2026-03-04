package dev.dunnam.diceanchors.anchor;

/** Selects the active {@link BudgetStrategy} implementation from configuration. */
public enum BudgetStrategyType {

    /** Count-only enforcement. Reproduces the original inline behavior. */
    COUNT,

    /**
     * Density-aware enforcement. Reduces effective budget when the conflict graph
     * exceeds the phase-transition threshold from Guo et al. (2025).
     */
    INTERFERENCE_DENSITY
}
