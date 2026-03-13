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
