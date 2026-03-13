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

import com.embabel.agent.api.identity.User;

/**
 * Minimal anonymous user identity for the arc-mem demo chat session.
 * <p>
 * Arc-mem does not have a persistent user model — all conversations
 * share a single anonymous DM context. This record satisfies the
 * {@link User} contract required by {@code Chatbot.createSession()}.
 */
public record AnonymousDmUser(String id) implements User {

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return "Adventurer";
    }

    @Override
    public String getUsername() {
        return id;
    }

    @Override
    public String getEmail() {
        return "";
    }
}
