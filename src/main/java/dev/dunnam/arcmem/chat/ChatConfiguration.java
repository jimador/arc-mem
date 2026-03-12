package dev.dunnam.diceanchors.chat;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.chat.Chatbot;
import com.embabel.chat.agent.AgentProcessChatbot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chat infrastructure configuration for the dice-anchors demo.
 * <p>
 * Follows the Impromptu {@code ChatConfiguration} pattern: creates the
 * {@link Chatbot} bean that {@code ChatView} uses to create per-session
 * agent processes. {@link ChatActions} is picked up automatically by
 * Embabel as an {@code @EmbabelComponent}.
 */
@Configuration
class ChatConfiguration {

    /**
     * Embabel chatbot backed by the agent platform.
     * Each call to {@code createSession()} starts a new agent process
     * scoped to one user session.
     */
    @Bean
    Chatbot chatbot(AgentPlatform agentPlatform) {
        return AgentProcessChatbot.utilityFromPlatform(agentPlatform);
    }
}
