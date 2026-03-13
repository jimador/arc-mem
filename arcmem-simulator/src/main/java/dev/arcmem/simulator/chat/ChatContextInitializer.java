package dev.arcmem.simulator.chat;
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

import com.embabel.dice.proposition.PropositionStatus;
import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.persistence.PropositionNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Seeds the chat context with pre-configured units on first message.
 * <p>
 * Reads {@code arc-mem.unit.chat-seed} configuration and creates
 * proposition nodes, promotes them to units, and optionally sets authority
 * above PROVISIONAL and pinned status.
 * <p>
 * Idempotent: checks for existing units by text (case-insensitive) before
 * creating duplicates. Safe to call on every message.
 */
@Service
public class ChatContextInitializer {

    private static final Logger logger = LoggerFactory.getLogger(ChatContextInitializer.class);

    private final ArcMemProperties properties;
    private final ArcMemEngine arcMemEngine;
    private final MemoryUnitRepository repository;

    public ChatContextInitializer(
            ArcMemProperties properties,
            ArcMemEngine arcMemEngine,
            MemoryUnitRepository repository) {
        this.properties = properties;
        this.arcMemEngine = arcMemEngine;
        this.repository = repository;
    }

    /**
     * Initialize the chat context with seed units from configuration.
     * <p>
     * Idempotent: existing units with matching text (case-insensitive) are skipped.
     * Authority above PROVISIONAL is set directly via repository, bypassing the
     * canonization gate (task 8.5).
     *
     * @param contextId the chat context to initialize
     */
    public void initializeContext(String contextId) {
        var unitConfig = properties.unit();
        var chatSeed = unitConfig.chatSeed();
        if (chatSeed == null || !chatSeed.enabled()) {
            return;
        }

        var seedUnits = chatSeed.units();
        if (seedUnits == null || seedUnits.isEmpty()) {
            return;
        }

        var existing = repository.findActiveUnits(contextId);
        var existingTexts = existing.stream()
                .map(node -> node.getText().toLowerCase())
                .toList();

        var seeded = 0;
        for (var seed : seedUnits) {
            if (existingTexts.contains(seed.text().toLowerCase())) {
                logger.debug("Seed unit already exists, skipping: {}", seed.text());
                continue;
            }

            var node = new PropositionNode(UUID.randomUUID().toString(), "default", seed.text(), 1.0, 0.0, null, List.of(),
                    Instant.now(), Instant.now(), PropositionStatus.ACTIVE, null, List.of());
            node.setContextId(contextId);
            repository.saveNode(node);

            arcMemEngine.promote(node.getId(), seed.rank());

            if (seed.authority().level() > Authority.PROVISIONAL.level()) {
                repository.setAuthority(node.getId(), seed.authority().name());
            }

            if (seed.pinned()) {
                repository.updatePinned(node.getId(), true);
            }

            seeded++;
        }

        if (seeded > 0) {
            logger.info("Initialized {} seed units for context {}", seeded, contextId);
        }
    }
}
