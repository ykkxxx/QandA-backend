package com.ykx.backend.service;

import com.ykx.backend.model.vo.rag.RagContextVO;

/**
 * RAG 全流程：向量化 → 向量检索 →（可选）距离过滤 → 重排过滤 → 上下文与提示词拼接。
 */
public interface RagService {

    /**
     * @param userId   与入库时一致的 user_id
     * @param question 用户问题
     */
    RagContextVO buildContext(String userId, String question);

    /**
     * 构建增强版 RAG 上下文（包含网络搜索结果）
     *
     * @param userId           与入库时一致的 user_id
     * @param question         用户问题
     * @param webSearchResults 网络搜索结果（可为空）
     */
    RagContextVO buildEnhancedContext(String userId, String question, String webSearchResults);
}
