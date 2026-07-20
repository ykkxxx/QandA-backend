package com.ykx.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ykx.backend.config.RagProperties;
import com.ykx.backend.config.VectorProperties;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import com.ykx.backend.model.vo.rag.RagContextVO;
import com.ykx.backend.rag.prompt.RagPromptBuilder;
import com.ykx.backend.service.RagService;
import com.ykx.backend.service.ReorderService;
import com.ykx.backend.service.VectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final VectorService vectorService;
    private final ReorderService reorderService;
    private final RagProperties ragProperties;
    private final VectorProperties vectorProperties;

    /**
     * 构建 RAG 上下文（核心方法：检索→过滤→重排→拼接）
     * 作用：根据用户问题，从向量库召回相关知识片段，交给 AI 使用
     *
     * @param userId   当前用户ID
     * @param question 用户问题
     * @return 封装好的 RAG 上下文（片段、文本、提示词）
     */
    @Override
    public RagContextVO buildContext(String userId, String question) {
        // 1. 参数校验：userId 和问题不能为空
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "userId 不能为空");
        }
        if (StrUtil.isBlank(question)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "问题不能为空");
        }

        // 2. 获取初始召回数量（默认 20 条）
        int initialK = Math.max(1,
                ragProperties.getRetrieval() != null
                        ? ragProperties.getRetrieval().getInitialTopK()
                        : 20);

        // 3. 向量化检索：根据用户问题，从 Milvus 召回相关文本片段
        List<Map<String, Object>> hits = vectorService.search(userId, question.trim(), initialK);

        // 4. 如果没检索到任何内容，打日志（方便排查：userId 不匹配是常见原因）
        if (hits.isEmpty()) {
            String db = vectorProperties.getMilvus() != null
                    ? vectorProperties.getMilvus().getDatabase()
                    : null;
            String coll = vectorProperties.getMilvus() != null
                    ? vectorProperties.getMilvus().getCollection()
                    : null;
            log.info(
                    "RAG：向量检索 0 条。当前 userId={}（来自登录 JWT），须与入库 /vector/save/text 的 userId 完全一致；Milvus database={} collection={}",
                    userId,
                    db != null ? db : "default",
                    coll != null ? coll : "?");
        }

        // 5. 向量距离过滤：根据 L2 距离过滤掉不相关的片段
        Double maxL2 = ragProperties.getVector() != null
                ? ragProperties.getVector().getMaxL2Distance()
                : null;
        if (maxL2 != null && !hits.isEmpty()) {
            int before = hits.size();
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> h : hits) {
                // 提取向量距离 score
                double d = extractVectorDistance(h);
                // 只保留距离 <= 阈值的片段
                if (d <= maxL2) {
                    filtered.add(h);
                }
            }
            hits = filtered;
            if (hits.isEmpty() && before > 0) {
                log.info("RAG：向量命中 {} 条，均被 rag.vector.max-l2-distance={} 过滤", before, maxL2);
            }
        }

        // 6. 重排序（rerank）：把最相关的片段排到前面
        List<Map<String, Object>> ranked = reorderService.rerank(question.trim(), hits);

        // 7. 数据标准化（防止原map被修改）
        List<Map<String, Object>> normalized = new ArrayList<>(ranked.size());
        for (Map<String, Object> row : ranked) {
            normalized.add(new HashMap<>(row));
        }

        // 8. 获取最大上下文字符数（默认 8000）
        int maxChars = Math.max(500,
                ragProperties.getPrompt() != null
                        ? ragProperties.getPrompt().getMaxContextChars()
                        : 8000);

        // 9. 把检索到的片段拼接成一段文本（给AI看）
        String contextText = RagPromptBuilder.concatRetrievedChunks(normalized, maxChars);

        // 10. 构建最终给大模型的用户提示词（问题+上下文）
        String llmUserContent = RagPromptBuilder.buildRagUserContent(question.trim(), contextText);

        // 11. 封装返回结果
        RagContextVO vo = new RagContextVO();
        vo.setChunks(normalized);          // 原始片段列表
        vo.setContextText(contextText);    // 拼接后的上下文文本
        vo.setLlmUserContent(llmUserContent); // 最终给LLM的提示词
        return vo;
    }

    /**
     * 构建增强版 RAG 上下文（包含网络搜索结果）
     */
    @Override
    public RagContextVO buildEnhancedContext(String userId, String question, String webSearchResults) {
        RagContextVO vo = buildContext(userId, question);
        
        if (StrUtil.isNotBlank(webSearchResults)) {
            vo.setWebSearchResults(webSearchResults);
            
            String enhancedContextText = vo.getContextText();
            if (StrUtil.isBlank(enhancedContextText)) {
                enhancedContextText = webSearchResults;
            } else {
                enhancedContextText = vo.getContextText() + "\n\n" + webSearchResults;
            }
            vo.setContextText(enhancedContextText);
            
            String enhancedLlmContent = RagPromptBuilder.buildEnhancedRagUserContent(question.trim(), vo.getContextText());
            vo.setLlmUserContent(enhancedLlmContent);
        }
        
        return vo;
    }

    /**
     * 从向量检索结果中提取距离分数（score）
     */
    private static double extractVectorDistance(Map<String, Object> h) {
        Object s = h.get("score");
        if (s instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(s));
        } catch (Exception e) {
            return Double.MAX_VALUE;
        }
    }
}
