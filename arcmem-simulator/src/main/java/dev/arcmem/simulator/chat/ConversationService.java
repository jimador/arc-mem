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

import dev.arcmem.core.persistence.MemoryUnitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConversationService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository repository;
    private final MemoryUnitRepository contextUnitRepository;

    public ConversationService(ConversationRepository repository, MemoryUnitRepository contextUnitRepository) {
        this.repository = repository;
        this.contextUnitRepository = contextUnitRepository;
    }

    public String createConversation() {
        return createConversation("Untitled");
    }

    public String createConversation(String title) {
        var conversationId = UUID.randomUUID().toString();
        repository.createConversation(conversationId, title);
        logger.info("Created conversation {}", conversationId);
        return conversationId;
    }

    public void appendMessage(String conversationId, String role, String text) {
        var ordinal = repository.countMessages(conversationId);
        repository.appendMessage(conversationId, role, text, ordinal);
    }

    public Optional<ConversationRecord> findConversation(String conversationId) {
        return repository.findConversation(conversationId);
    }

    public List<ChatMessageRecord> loadConversation(String conversationId) {
        return repository.loadMessages(conversationId);
    }

    public List<ConversationRecord> listConversations() {
        return repository.listConversations();
    }

    public String cloneConversation(String sourceConversationId) {
        var source = findConversation(sourceConversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Source conversation not found: " + sourceConversationId));

        var newConversationId = createConversation("Clone of: " + source.title());

        var messages = loadConversation(sourceConversationId);
        for (var msg : messages) {
            appendMessage(newConversationId, msg.role(), msg.text());
        }

        var units = contextUnitRepository.findActiveUnits(sourceConversationId);
        for (var unit : units) {
            contextUnitRepository.saveNode(unit.cloneForContext(newConversationId));
        }

        logger.info("Cloned conversation {} to {} with {} messages and {} units",
                sourceConversationId, newConversationId, messages.size(), units.size());

        return newConversationId;
    }
}
