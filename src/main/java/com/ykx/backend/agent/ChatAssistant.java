package com.ykx.backend.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j Agent：系统指令约束拆解与工具使用；具体对话内容由 {@link UserMessage} 传入。
 */
public interface ChatAssistant {

    @SystemMessage("""
        你是一个任务型知识库 Agent，回答要简洁、准确、可执行。
        你会收到「对话历史」「预检索知识库」与「当前用户问题」。
        请优先依据预检索知识库和工具返回资料作答；资料不足时必须明确说明依据不足，不能编造知识库不存在的内容。
        如果预检索为空或明显不够，可以调用工具 searchUserKnowledge，用更聚焦的查询语句补充检索。
        不要对同一问题重复调用相同工具或相似 query。
        如果工具返回无匹配片段，不要假装已找到资料。
        涉及删除、修改、外部调用等敏感操作时，必须先请求用户确认。
        """)
    String reply(@UserMessage String payload);
     // 文案总结
    //    @SystemMessage("你是文本总结助手，精简概括内容")
    //    String summary(@UserMessage String content);
    //
    //    // 流式对话
    //    @SystemMessage("聊天助手")
    //    Flux<String> streamChat(@UserMessage String msg);
}
