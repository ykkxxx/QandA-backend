package com.ykx.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {
    /** 单次回答最多工具调用次数 */
    private int maxToolCalls = 3;

    /** 单次回答最多知识库检索工具调用次数 */
    private int maxKnowledgeSearchCalls = 2;

    /** 拼进 Prompt 的最大历史消息条数 */
    private int maxHistoryMessages = 20;

    /** 工具输出落日志时最大保留字符数 */
    private int toolOutputMaxChars = 1000;
}
