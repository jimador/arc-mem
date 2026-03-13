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
import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.persistence.PropositionNode;
import dev.arcmem.simulator.history.SimulationRunRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SimToChatBridge {

    private static final Logger logger = LoggerFactory.getLogger(SimToChatBridge.class);

    private final ConversationService conversationService;
    private final MemoryUnitRepository contextUnitRepository;
    private final ArcMemEngine arcMemEngine;

    public SimToChatBridge(ConversationService conversationService,
                           MemoryUnitRepository contextUnitRepository,
                           ArcMemEngine arcMemEngine) {
        this.conversationService = conversationService;
        this.contextUnitRepository = contextUnitRepository;
        this.arcMemEngine = arcMemEngine;
    }

    public String cloneRunToConversation(SimulationRunRecord run) {
        var conversationId = conversationService.createConversation("From sim: " + run.scenarioId());

        for (var snapshot : run.turnSnapshots()) {
            if (snapshot.playerMessage() != null && !snapshot.playerMessage().isBlank()) {
                conversationService.appendMessage(conversationId, "PLAYER", snapshot.playerMessage());
            }
            if (snapshot.dmResponse() != null && !snapshot.dmResponse().isBlank()) {
                conversationService.appendMessage(conversationId, "DM", snapshot.dmResponse());
            }
        }

        for (var unit : run.finalUnitState()) {
            var node = new PropositionNode(UUID.randomUUID().toString(), "default", unit.text(), unit.confidence(), 0.0, null, List.of(),
                    Instant.now(), Instant.now(), PropositionStatus.ACTIVE, null, List.of());
            node.setContextId(conversationId);
            node.setReinforcementCount(unit.reinforcementCount());
            node.setImportance(unit.diceImportance());
            node.setDecay(unit.diceDecay());
            contextUnitRepository.saveNode(node);

            arcMemEngine.promote(node.getId(), unit.rank());

            if (unit.authority().level() > Authority.PROVISIONAL.level()) {
                contextUnitRepository.setAuthority(node.getId(), unit.authority().name());
            }

            if (unit.pinned()) {
                contextUnitRepository.updatePinned(node.getId(), true);
            }
        }

        logger.info("Cloned sim run {} ({}) to conversation {} with {} messages and {} units",
                run.runId(), run.scenarioId(), conversationId,
                run.turnSnapshots().size(), run.finalUnitState().size());

        return conversationId;
    }
}
