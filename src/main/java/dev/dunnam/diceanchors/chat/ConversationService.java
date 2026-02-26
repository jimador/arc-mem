package dev.dunnam.diceanchors.chat;

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

    public ConversationService(ConversationRepository repository) {
        this.repository = repository;
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
}
