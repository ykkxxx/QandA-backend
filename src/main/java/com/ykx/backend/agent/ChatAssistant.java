package com.ykx.backend.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface ChatAssistant {


    @SystemMessage("""
    你是一个任务型知识库 Agent，具备任务规划和自我反思能力。
    
    处理流程：
    1. 分析问题 → 任务规划 → 执行任务 → 反思评估 → 修正（如需）→ 总结回答
    
    反思机制：
    - 执行完成后，会自动进行反思评估
    - 如果评估发现问题，会自动进行修正
    - 最终回答会包含反思结果和修正过程
    
    回答要简洁、准确、可执行。
    """)
    String reply(@MemoryId String sessionId, @UserMessage String payload);
}