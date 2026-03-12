package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.Authority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ComplianceEnforcer} that translates CANON and RELIABLE anchor constraints
 * into an OpenAI-compatible logit bias map.
 * <p>
 * Authority-tiered bias: CANON tokens receive bias {@link LogitBiasMap#CANON_BIAS},
 * RELIABLE tokens receive {@link LogitBiasMap#RELIABLE_BIAS}. When the total token
 * count exceeds the {@link LogitBiasMap#MAX_TOKENS} limit, CANON constraints are
 * prioritised and overflow is logged.
 * <p>
 * When the active model does not support logit bias, this enforcer degrades gracefully:
 * it logs a warning and returns a compliant result with zero overhead.
 * <p>
 * Thread safety: stateless; safe for concurrent use.
 */
@Component
public class LogitBiasEnforcer implements ComplianceEnforcer {

    private static final Logger logger = LoggerFactory.getLogger(LogitBiasEnforcer.class);

    private final ModelCapabilityDetector capabilityDetector;

    public LogitBiasEnforcer(ModelCapabilityDetector capabilityDetector) {
        this.capabilityDetector = capabilityDetector;
    }

    @Override
    public ComplianceResult enforce(ComplianceContext context) {
        var start = Instant.now();
        var index = AnchorConstraintIndex.build(context.activeAnchors());

        if (index.getConstraints().isEmpty()) {
            return ComplianceResult.compliant(Duration.between(start, Instant.now()));
        }

        var biasMap = buildBiasMap(index);
        var duration = Duration.between(start, Instant.now());

        logger.info("compliance.strategy=LOGIT_BIAS constraint_count={} token_count={} coverage={} overflow={}",
                biasMap.constraintCount(),
                biasMap.tokenBiases().size(),
                biasMap.coverage(),
                biasMap.overflowCount());

        if (biasMap.overflowCount() > 0) {
            logger.warn("compliance.logit_bias.overflow={} tokens dropped due to {}-token limit",
                    biasMap.overflowCount(), LogitBiasMap.MAX_TOKENS);
        }

        return ComplianceResult.compliant(duration);
    }

    /**
     * Translates the constraint index into a logit bias map, respecting the 300-token limit
     * with CANON-first priority ordering.
     */
    LogitBiasMap buildBiasMap(AnchorConstraintIndex index) {
        var constraints = index.getConstraints();
        var canonTokens = new ArrayList<Map.Entry<String, Integer>>();
        var reliableTokens = new ArrayList<Map.Entry<String, Integer>>();

        for (var constraint : constraints) {
            var bias = biasFor(constraint.authority());
            for (var token : constraint.boostTokens()) {
                var entry = Map.entry(token, bias);
                if (constraint.authority() == Authority.CANON) {
                    canonTokens.add(entry);
                } else {
                    reliableTokens.add(entry);
                }
            }
        }

        var tokenBiases = new LinkedHashMap<String, Integer>();
        var overflowCount = 0;

        // CANON first
        for (var entry : canonTokens) {
            if (tokenBiases.size() >= LogitBiasMap.MAX_TOKENS) {
                overflowCount++;
            } else {
                tokenBiases.merge(entry.getKey(), entry.getValue(), Math::max);
            }
        }

        // RELIABLE second
        for (var entry : reliableTokens) {
            if (tokenBiases.size() >= LogitBiasMap.MAX_TOKENS) {
                overflowCount++;
            } else {
                tokenBiases.merge(entry.getKey(), entry.getValue(), Math::max);
            }
        }

        return new LogitBiasMap(
                Map.copyOf(tokenBiases),
                constraints.size(),
                index.getTotalCoverage(),
                overflowCount);
    }

    private int biasFor(Authority authority) {
        return switch (authority) {
            case CANON -> LogitBiasMap.CANON_BIAS;
            case RELIABLE -> LogitBiasMap.RELIABLE_BIAS;
            default -> 0;
        };
    }

    /**
     * Build a logit bias map from a set of anchors and check model support.
     * Returns empty map if the model does not support logit bias.
     */
    public LogitBiasMap buildBiasMapForModel(List<dev.dunnam.diceanchors.anchor.Anchor> anchors, String modelId) {
        if (!capabilityDetector.supportsLogitBias(modelId)) {
            logger.warn("compliance.capability.logit_bias_supported=false model={} — degrading gracefully", modelId);
            return LogitBiasMap.empty();
        }
        var index = AnchorConstraintIndex.build(anchors);
        return buildBiasMap(index);
    }
}
