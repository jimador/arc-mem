package dev.dunnam.diceanchors.chat;

import dev.dunnam.diceanchors.persistence.AnchorRepository;
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
    private final AnchorRepository anchorRepository;

    public ConversationService(ConversationRepository repository, AnchorRepository anchorRepository) {
        this.repository = repository;
        this.anchorRepository = anchorRepository;
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

        var anchors = anchorRepository.findActiveAnchors(sourceConversationId);
        for (var anchor : anchors) {
            anchorRepository.saveNode(anchor.cloneForContext(newConversationId));
        }

        logger.info("Cloned conversation {} to {} with {} messages and {} anchors",
                sourceConversationId, newConversationId, messages.size(), anchors.size());

        return newConversationId;
    }
}
