package dev.dunnam.diceanchors.chat;

import com.embabel.agent.api.identity.User;

/**
 * Minimal anonymous user identity for the dice-anchors demo chat session.
 * <p>
 * Dice-anchors does not have a persistent user model — all conversations
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
