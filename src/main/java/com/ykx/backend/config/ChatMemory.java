package com.ykx.backend.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ChatMemory {
    private final AgentProperties agentProperties;
    @Bean
    public ChatMemoryProvider chatMemoryProvider(){
        return sessionId -> MessageWindowChatMemory.builder()
                .id(sessionId)
                .maxMessages(agentProperties.getMaxHistoryMessages())
                .build();
    }
}
