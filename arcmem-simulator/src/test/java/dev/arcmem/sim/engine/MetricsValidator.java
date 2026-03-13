package dev.arcmem.simulator.engine;
import dev.arcmem.core.memory.budget.*;
import dev.arcmem.core.memory.canon.*;
import dev.arcmem.core.memory.conflict.*;
import dev.arcmem.core.memory.engine.*;
import dev.arcmem.core.memory.maintenance.*;
import dev.arcmem.core.memory.model.*;
import dev.arcmem.core.memory.mutation.*;
import dev.arcmem.core.memory.trust.*;
import dev.arcmem.core.assembly.budget.*;
import dev.arcmem.core.assembly.compaction.*;
import dev.arcmem.core.assembly.compliance.*;
import dev.arcmem.core.assembly.protection.*;
import dev.arcmem.core.assembly.retrieval.*;

import dev.arcmem.core.spi.llm.*;
import dev.arcmem.simulator.engine.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for validating actual metric values against operator expressions.
 * <p>
 * Supports expressions like {@code ">0.95"}, {@code "== 0"}, {@code ">=0.9"},
 * {@code "<5"}, {@code "<=100"}, {@code "!= 0"}.
 * Whitespace between operator and number is tolerated.
 */
final class MetricsValidator {

    private static final Pattern EXPRESSION_PATTERN =
            Pattern.compile("^\\s*(>=|<=|!=|==|>|<)\\s*(-?\\d+\\.?\\d*)\\s*$");

    private MetricsValidator() {}

    /**
     * Validate a single metric value against an operator expression.
     *
     * @param metricName the human-readable metric name (for error messages)
     * @param actual     the actual metric value
     * @param expression the expected expression (e.g., {@code ">0.95"})
     * @return validation result indicating pass/fail with detail
     * @throws IllegalArgumentException if the expression cannot be parsed
     */
    static ValidationResult validate(String metricName, double actual, String expression) {
        Matcher matcher = EXPRESSION_PATTERN.matcher(expression);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Invalid metric expression '%s' for metric '%s'. Expected format: <operator><number> (e.g., '>0.95', '== 0')"
                            .formatted(expression, metricName));
        }
        var operator = matcher.group(1);
        var expected = Double.parseDouble(matcher.group(2));
        var passed = evaluate(operator, actual, expected);
        return new ValidationResult(metricName, passed, actual, expression);
    }

    /**
     * Validate multiple metrics at once.
     *
     * @param checks list of metric checks to perform
     * @return aggregated result with individual details
     */
    static AggregateResult validateAll(List<MetricCheck> checks) {
        var results = new ArrayList<ValidationResult>();
        for (var check : checks) {
            results.add(validate(check.metricName(), check.actual(), check.expression()));
        }
        return new AggregateResult(results);
    }

    private static boolean evaluate(String operator, double actual, double expected) {
        return switch (operator) {
            case ">"  -> actual > expected;
            case "<"  -> actual < expected;
            case ">=" -> actual >= expected;
            case "<=" -> actual <= expected;
            case "==" -> Double.compare(actual, expected) == 0;
            case "!=" -> Double.compare(actual, expected) != 0;
            default   -> throw new IllegalArgumentException("Unknown operator: " + operator);
        };
    }

    /**
     * A single metric check to perform.
     */
    record MetricCheck(String metricName, double actual, String expression) {}

    /**
     * Result of validating one metric.
     */
    record ValidationResult(String metricName, boolean passed, double actual, String expression) {

        String failureMessage() {
            return "Metric '%s' failed: actual=%s did not satisfy expression '%s'"
                    .formatted(metricName, actual, expression);
        }
    }

    /**
     * Aggregated result of multiple metric validations.
     */
    record AggregateResult(List<ValidationResult> results) {

        boolean allPassed() {
            return results.stream().allMatch(ValidationResult::passed);
        }

        List<ValidationResult> failures() {
            return results.stream().filter(r -> !r.passed()).toList();
        }

        String failureSummary() {
            var sb = new StringBuilder();
            for (var failure : failures()) {
                sb.append(failure.failureMessage()).append("\n");
            }
            return sb.toString().stripTrailing();
        }
    }
}
