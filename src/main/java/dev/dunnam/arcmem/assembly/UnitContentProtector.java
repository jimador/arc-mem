package dev.dunnam.diceanchors.assembly;

import dev.dunnam.diceanchors.anchor.AnchorEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Protects messages backing active anchors from compaction.
 * Protection priority equals the anchor's rank (higher rank = higher protection).
 */
@Component
public class AnchorContentProtector implements ProtectedContentProvider {

    private static final Logger logger = LoggerFactory.getLogger(AnchorContentProtector.class);

    private final AnchorEngine anchorEngine;

    public AnchorContentProtector(AnchorEngine anchorEngine) {
        this.anchorEngine = anchorEngine;
    }

    @Override
    public List<ProtectedContent> getProtectedContent(String contextId) {
        var anchors = anchorEngine.inject(contextId);
        var result = anchors.stream()
                            .map(anchor -> new ProtectedContent(
                                    anchor.id(),
                                    anchor.text(),
                                    anchor.rank(),
                                    "Active anchor [%s] rank=%d".formatted(anchor.authority().name(), anchor.rank())
                            ))
                            .toList();
        logger.debug("Protected {} anchor-backed items for context {}", result.size(), contextId);
        return result;
    }
}
