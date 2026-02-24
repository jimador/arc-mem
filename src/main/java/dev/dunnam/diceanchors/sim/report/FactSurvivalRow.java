package dev.dunnam.diceanchors.sim.report;

import java.util.Map;

/**
 * Per-fact survival data across all conditions for a single ground truth fact.
 *
 * @param factId           stable fact identifier from scenario ground truth
 * @param factText         human-readable fact text
 * @param conditionResults condition name to survival result
 */
public record FactSurvivalRow(
        String factId,
        String factText,
        Map<String, FactConditionResult> conditionResults) {
}
