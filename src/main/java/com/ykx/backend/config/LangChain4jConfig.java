package com.ykx.backend.config;

import com.ykx.backend.ai.lc.ChatAssistant;
import com.ykx.backend.ai.lc.KnowledgeTools;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Configuration
public class LangChain4jConfig {

    @Bean
    //创建模型
    public ChatLanguageModel chatLanguageModel(DeepSeekAiProperties props) {
        String baseUrl = toOpenAiCompatibleBaseUrl(props.getApiUrl());
        String model = StringUtils.hasText(props.getModel()) ? props.getModel() : "deepseek-chat";
        String apiKey = props.getApiKey() != null ? props.getApiKey() : "";
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(model)
                .timeout(Duration.ofMinutes(2))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean
    public ChatAssistant chatAssistant(ChatLanguageModel chatLanguageModel, KnowledgeTools knowledgeTools) {
        return AiServices.builder(ChatAssistant.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(knowledgeTools)
                .build();
    }

    /**
     * 将常见完整 chat URL 规范为 OpenAI 兼容 base（含 /v1），供 LangChain4j 拼接路径。
     */
    static String toOpenAiCompatibleBaseUrl(String apiUrl) {
        if (!StringUtils.hasText(apiUrl)) {
            return "https://api.deepseek.com/v1";
        }
        String u = apiUrl.trim();
        if (u.endsWith("/chat/completions")) {
            return u.substring(0, u.length() - "/chat/completions".length());
        }
        return u;
    }
}
