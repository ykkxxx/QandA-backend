package com.ykx.backend.model.vo.rag;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * RAG 流水线输出：检索片段、拼接上下文、可直接喂给大模型的用户侧正文。
 */
@Data
public class RagContextVO {

    /** 进入上下文的片段（含 vector_score、rerank_score 等） */
    private List<Map<String, Object>> chunks;

    /** 仅参考资料正文 */
    private String contextText;

    /** 建议作为大模型「用户消息」的完整正文（含答题约束 + 问题 + 参考资料） */
    private String llmUserContent;
}
