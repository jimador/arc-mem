package dev.dunnam.diceanchors.chat;

import com.embabel.chat.Conversation;
import org.springframework.context.ApplicationEvent;

/**
 * Published after a conversation exchange (user message + assistant response).
 * Triggers async DICE proposition extraction via {@link ConversationPropositionExtraction}.
 * <p>
 * Follows the Impromptu {@code ConversationAnalysisRequestEvent} pattern.
 */
public class ConversationAnalysisRequestEvent extends ApplicationEvent {

    private final Conversation conversation;
    private final String contextId;

    public ConversationAnalysisRequestEvent(Object source, String contextId, Conversation conversation) {
        super(source);
        this.contextId = contextId;
        this.conversation = conversation;
    }

    public String getContextId() {
        return contextId;
    }

    public Conversation getConversation() {
        return conversation;
    }
}
