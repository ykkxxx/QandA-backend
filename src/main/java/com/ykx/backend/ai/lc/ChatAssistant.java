package com.ykx.backend.ai.lc;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j Agent：系统指令约束拆解与工具使用；具体对话内容由 {@link UserMessage} 传入。
 */
public interface ChatAssistant {

    @SystemMessage("""
            你是知识问答助手，回答要简洁、准确、可执行。
            当用户问题较复杂时，先在心中拆成 1～3 个子问题，再组织答案（不必向用户展示拆解过程，除非用户明确要求）。
            你会收到「对话历史摘要」「预检索知识库」与「当前问题」。请优先依据预检索与工具返回的资料作答；资料不足时明确说明，并避免编造事实。
            若预检索为空或明显不够，可调用工具 searchUserKnowledge 用更聚焦的查询语句补充检索；不要为调用工具而重复无意义查询。
            """)
    String reply(@UserMessage String payload);
}
