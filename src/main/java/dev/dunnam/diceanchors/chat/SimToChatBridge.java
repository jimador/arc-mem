package dev.dunnam.diceanchors.chat;

import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.persistence.PropositionNode;
import dev.dunnam.diceanchors.sim.engine.SimulationRunRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SimToChatBridge {

    private static final Logger logger = LoggerFactory.getLogger(SimToChatBridge.class);

    private final ConversationService conversationService;
    private final AnchorRepository anchorRepository;
    private final AnchorEngine anchorEngine;

    public SimToChatBridge(ConversationService conversationService,
                           AnchorRepository anchorRepository,
                           AnchorEngine anchorEngine) {
        this.conversationService = conversationService;
        this.anchorRepository = anchorRepository;
        this.anchorEngine = anchorEngine;
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

        for (var anchor : run.finalAnchorState()) {
            var node = new PropositionNode(anchor.text(), anchor.confidence());
            node.setContextId(conversationId);
            node.setReinforcementCount(anchor.reinforcementCount());
            node.setImportance(anchor.diceImportance());
            node.setDecay(anchor.diceDecay());
            anchorRepository.saveNode(node);

            anchorEngine.promote(node.getId(), anchor.rank());

            if (anchor.authority().level() > Authority.PROVISIONAL.level()) {
                anchorRepository.setAuthority(node.getId(), anchor.authority().name());
            }

            if (anchor.pinned()) {
                anchorRepository.updatePinned(node.getId(), true);
            }
        }

        logger.info("Cloned sim run {} ({}) to conversation {} with {} messages and {} anchors",
                run.runId(), run.scenarioId(), conversationId,
                run.turnSnapshots().size(), run.finalAnchorState().size());

        return conversationId;
    }
}
