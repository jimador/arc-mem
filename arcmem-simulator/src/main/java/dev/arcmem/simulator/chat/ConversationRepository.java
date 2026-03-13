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

import org.drivine.manager.PersistenceManager;
import org.drivine.query.QuerySpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ConversationRepository {

    private static final Logger logger = LoggerFactory.getLogger(ConversationRepository.class);

    private static final String INDEX_CONVERSATION = """
            CREATE INDEX conversation_id_idx IF NOT EXISTS
            FOR (c:Conversation) ON (c.conversationId)
            """;

    private static final String INDEX_CHAT_MESSAGE = """
            CREATE INDEX chat_message_conversation_idx IF NOT EXISTS
            FOR (m:ChatMessage) ON (m.conversationId)
            """;

    private static final String CREATE_CONVERSATION = """
            MERGE (c:Conversation {conversationId: $conversationId})
            ON CREATE SET c.title = $title, c.createdAt = $createdAt
            """;

    private static final String FIND_CONVERSATION = """
            MATCH (c:Conversation {conversationId: $conversationId})
            OPTIONAL MATCH (m:ChatMessage {conversationId: c.conversationId})
            WITH c, count(m) AS msgCount
            RETURN {conversationId: c.conversationId, title: c.title, createdAt: c.createdAt, messageCount: msgCount} AS result
            """;

    private static final String APPEND_MESSAGE = """
            CREATE (m:ChatMessage {
                conversationId: $conversationId,
                role: $role,
                text: $text,
                ordinal: $ordinal,
                createdAt: $createdAt
            })
            """;

    private static final String LOAD_MESSAGES = """
            MATCH (m:ChatMessage {conversationId: $conversationId})
            RETURN {conversationId: m.conversationId, role: m.role, text: m.text, ordinal: m.ordinal, createdAt: m.createdAt} AS result
            ORDER BY m.ordinal ASC
            """;

    private static final String COUNT_MESSAGES = """
            MATCH (m:ChatMessage {conversationId: $conversationId})
            RETURN count(m)
            """;

    private static final String LIST_CONVERSATIONS = """
            MATCH (c:Conversation)
            OPTIONAL MATCH (m:ChatMessage {conversationId: c.conversationId})
            WITH c, count(m) AS msgCount
            RETURN {conversationId: c.conversationId, title: c.title, createdAt: c.createdAt, messageCount: msgCount} AS result
            ORDER BY c.createdAt DESC
            """;

    private final PersistenceManager persistenceManager;

    public ConversationRepository(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @PostConstruct
    void provision() {
        try {
            persistenceManager.execute(QuerySpecification.withStatement(INDEX_CONVERSATION));
            persistenceManager.execute(QuerySpecification.withStatement(INDEX_CHAT_MESSAGE));
            logger.info("Provisioned conversation indexes");
        } catch (Exception e) {
            logger.warn("Could not create conversation indexes: {}", e.getMessage());
        }
    }

    @Transactional
    public void createConversation(String conversationId, String title) {
        persistenceManager.execute(
                QuerySpecification.withStatement(CREATE_CONVERSATION)
                        .bind(Map.of(
                                "conversationId", conversationId,
                                "title", title,
                                "createdAt", Instant.now().toString()
                        ))
        );
        logger.debug("Created conversation {}", conversationId);
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Optional<ConversationRecord> findConversation(String conversationId) {
        var results = persistenceManager.query(
                QuerySpecification.withStatement(FIND_CONVERSATION)
                        .bind(Map.of("conversationId", conversationId))
                        .transform(Map.class)
        );
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toConversationRecord((Map<String, Object>) results.getFirst()));
    }

    @Transactional
    public void appendMessage(String conversationId, String role, String text, int ordinal) {
        persistenceManager.execute(
                QuerySpecification.withStatement(APPEND_MESSAGE)
                        .bind(Map.of(
                                "conversationId", conversationId,
                                "role", role,
                                "text", text,
                                "ordinal", ordinal,
                                "createdAt", Instant.now().toString()
                        ))
        );
        logger.debug("Appended message ordinal {} to conversation {}", ordinal, conversationId);
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<ChatMessageRecord> loadMessages(String conversationId) {
        var results = persistenceManager.query(
                QuerySpecification.withStatement(LOAD_MESSAGES)
                        .bind(Map.of("conversationId", conversationId))
                        .transform(Map.class)
        );
        return results.stream()
                .map(r -> toChatMessageRecord((Map<String, Object>) r))
                .toList();
    }

    @Transactional(readOnly = true)
    public int countMessages(String conversationId) {
        var results = persistenceManager.query(
                QuerySpecification.withStatement(COUNT_MESSAGES)
                        .bind(Map.of("conversationId", conversationId))
                        .transform(Long.class)
        );
        return results.isEmpty() ? 0 : results.getFirst().intValue();
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<ConversationRecord> listConversations() {
        var results = persistenceManager.query(
                QuerySpecification.withStatement(LIST_CONVERSATIONS)
                        .transform(Map.class)
        );
        return results.stream()
                .map(r -> toConversationRecord((Map<String, Object>) r))
                .toList();
    }

    private ConversationRecord toConversationRecord(Map<String, Object> row) {
        return new ConversationRecord(
                (String) row.get("conversationId"),
                (String) row.get("title"),
                Instant.parse((String) row.get("createdAt")),
                ((Number) row.get("messageCount")).intValue()
        );
    }

    private ChatMessageRecord toChatMessageRecord(Map<String, Object> row) {
        return new ChatMessageRecord(
                (String) row.get("conversationId"),
                (String) row.get("role"),
                (String) row.get("text"),
                ((Number) row.get("ordinal")).intValue(),
                Instant.parse((String) row.get("createdAt"))
        );
    }
}
