package dev.dunnam.diceanchors.assembly;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Composes multiple {@link ComplianceEnforcer} layers into a single enforcer.
 * <p>
 * Layers are executed in order. Result aggregation:
 * <ul>
 *   <li>{@code compliant} — all layers compliant</li>
 *   <li>{@code violations} — union of all layer violations</li>
 *   <li>{@code suggestedAction} — most severe across layers (REJECT > RETRY > ACCEPT)</li>
 *   <li>{@code validationDuration} — sum of all layer durations</li>
 * </ul>
 * Graceful degradation: if a layer throws, it is skipped and a warning is logged.
 */
public class HybridComplianceEnforcer implements ComplianceEnforcer {

    private static final Logger logger = LoggerFactory.getLogger(HybridComplianceEnforcer.class);

    private final List<ComplianceEnforcer> layers;

    public HybridComplianceEnforcer(List<ComplianceEnforcer> layers) {
        this.layers = List.copyOf(layers);
    }

    @Override
    public ComplianceResult enforce(ComplianceContext context) {
        var allViolations = new ArrayList<ComplianceViolation>();
        var compliant = true;
        var worstAction = ComplianceAction.ACCEPT;
        var totalDuration = Duration.ZERO;

        for (var layer : layers) {
            ComplianceResult result;
            try {
                result = layer.enforce(context);
            } catch (Exception e) {
                logger.warn("compliance.hybrid layer={} failed — skipping: {}",
                        layer.getClass().getSimpleName(), e.getMessage());
                continue;
            }
            allViolations.addAll(result.violations());
            if (!result.compliant()) {
                compliant = false;
            }
            worstAction = mostSevere(worstAction, result.suggestedAction());
            totalDuration = totalDuration.plus(result.validationDuration());
        }

        logger.info("compliance.strategy=HYBRID layers={} compliant={} action={} duration={}ms",
                layers.size(), compliant, worstAction, totalDuration.toMillis());

        return new ComplianceResult(compliant, List.copyOf(allViolations), worstAction, totalDuration);
    }

    private ComplianceAction mostSevere(ComplianceAction current, ComplianceAction candidate) {
        return switch (candidate) {
            case REJECT -> ComplianceAction.REJECT;
            case RETRY -> current == ComplianceAction.REJECT ? ComplianceAction.REJECT : ComplianceAction.RETRY;
            case ACCEPT -> current;
        };
    }
}
