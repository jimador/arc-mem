package dev.dunnam.diceanchors.chat;

import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.persistence.PropositionNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Seeds the chat context with pre-configured anchors on first message.
 * <p>
 * Reads {@code dice-anchors.anchor.chat-seed} configuration and creates
 * proposition nodes, promotes them to anchors, and optionally sets authority
 * above PROVISIONAL and pinned status.
 * <p>
 * Idempotent: checks for existing anchors by text (case-insensitive) before
 * creating duplicates. Safe to call on every message.
 */
@Service
public class ChatContextInitializer {

    private static final Logger logger = LoggerFactory.getLogger(ChatContextInitializer.class);

    private final DiceAnchorsProperties properties;
    private final AnchorEngine anchorEngine;
    private final AnchorRepository repository;

    public ChatContextInitializer(
            DiceAnchorsProperties properties,
            AnchorEngine anchorEngine,
            AnchorRepository repository) {
        this.properties = properties;
        this.anchorEngine = anchorEngine;
        this.repository = repository;
    }

    /**
     * Initialize the chat context with seed anchors from configuration.
     * <p>
     * Idempotent: existing anchors with matching text (case-insensitive) are skipped.
     * Authority above PROVISIONAL is set directly via repository, bypassing the
     * canonization gate (task 8.5).
     *
     * @param contextId the chat context to initialize
     */
    public void initializeContext(String contextId) {
        var anchorConfig = properties.anchor();
        var chatSeed = anchorConfig.chatSeed();
        if (chatSeed == null || !chatSeed.enabled()) {
            return;
        }

        var seedAnchors = chatSeed.anchors();
        if (seedAnchors == null || seedAnchors.isEmpty()) {
            return;
        }

        var existing = repository.findActiveAnchors(contextId);
        var existingTexts = existing.stream()
                .map(node -> node.getText().toLowerCase())
                .toList();

        var seeded = 0;
        for (var seed : seedAnchors) {
            if (existingTexts.contains(seed.text().toLowerCase())) {
                logger.debug("Seed anchor already exists, skipping: {}", seed.text());
                continue;
            }

            var node = new PropositionNode(seed.text(), 1.0);
            node.setContextId(contextId);
            repository.saveNode(node);

            anchorEngine.promote(node.getId(), seed.rank());

            var authority = Authority.valueOf(seed.authority());
            if (authority.level() > Authority.PROVISIONAL.level()) {
                repository.setAuthority(node.getId(), authority.name());
            }

            if (seed.pinned()) {
                repository.updatePinned(node.getId(), true);
            }

            seeded++;
        }

        if (seeded > 0) {
            logger.info("Initialized {} seed anchors for context {}", seeded, contextId);
        }
    }
}
